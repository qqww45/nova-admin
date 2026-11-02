import { BrowserWindow } from 'electron'
import { getDb } from '../db'
import { getKingdee } from '../kingdee/client'
import { buildLineCandidates } from '../kingdee/lineKeys'
import { asRow, cellNumber, cellScalar, cellTypeName } from '../kingdee/cells'
import { loadOutstockMapping } from '../mappingStore'
import { tryAcquire, release } from '../businessLock'
import type { LastSyncStat, SyncEntity, SyncProgress, SyncStartInput } from '../../shared/types'

function emit(progress: SyncProgress) {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('sync:progress', progress)
  }
}

function nowIso() {
  return new Date().toISOString()
}

async function syncCustomers(sinceDate: string): Promise<SyncProgress> {
  const client = getKingdee()
  const db = getDb()
  const started = nowIso()
  db.prepare(
    `INSERT INTO sync_run(entity, status, row_count, message, started_at) VALUES(?,?,?,?,?)`
  ).run('customer', 'running', 0, '', started)

  try {
    const fieldKeys = 'FCUSTID,FNumber,FName,FADDRESS,FForbidStatus'
    let start = 0
    let total = 0
    const upsert = db.prepare(`
      INSERT INTO customer(fid, fnumber, fname, address, disabled, sync_at)
      VALUES(@fid, @fnumber, @fname, @address, @disabled, @sync_at)
      ON CONFLICT(fid) DO UPDATE SET
        fnumber=excluded.fnumber,
        fname=excluded.fname,
        address=excluded.address,
        disabled=excluded.disabled,
        sync_at=excluded.sync_at
    `)

    // eslint-disable-next-line no-constant-condition
    while (true) {
      const rows = await client.executeBillQuery({
        FormId: 'BD_Customer',
        FieldKeys: fieldKeys,
        FilterString: sinceDate ? '' : '',
        StartRow: start,
        Limit: 2000
      })
      const tx = db.transaction((batch: unknown[][]) => {
        for (const raw of batch) {
          const r = asRow(raw)
          upsert.run({
            fid: cellNumber(r[0]),
            fnumber: cellScalar(r[1]),
            fname: cellScalar(r[2]),
            address: cellScalar(r[3]),
            disabled: cellScalar(r[4]) === 'B' ? 1 : 0,
            sync_at: nowIso()
          })
        }
      })
      tx(rows)
      total += rows.length
      if (rows.length < 2000) break
      start += 2000
    }

    const finished = nowIso()
    db.prepare(
      `UPDATE sync_run SET status=?, row_count=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run('success', total, 'ok', finished, 'customer', started)

    return {
      entity: 'customer',
      status: 'success',
      rowCount: total,
      message: `客户 ${total} 条`,
      finishedAt: finished
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    const status: SyncProgress['status'] =
      /权限|permission|denied|无权限/i.test(msg) ? 'skipped_no_perm' : 'failed'
    db.prepare(
      `UPDATE sync_run SET status=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run(status, msg, nowIso(), 'customer', started)
    return { entity: 'customer', status, rowCount: 0, message: msg, finishedAt: nowIso() }
  }
}

/** 出库/订单完整字段以 M0 映射为准；字段集外置 outstock.json（用户目录优先），候选集自动降级 */
type LineCol =
  | 'fid' | 'billNo' | 'date' | 'custFid' | 'custName' | 'status' | 'cancelled'
  | 'entryId' | 'matNo' | 'matName' | 'unit' | 'qty' | 'price' | 'amount'
  | 'srcNo' | 'remark' | 'po' | 'other'

function classifyLineToken(token: string, poField: string): LineCol {
  const t = token.trim()
  const k = t.toUpperCase()
  if (k === 'FID') return 'fid'
  if (k === 'FBILLNO') return 'billNo'
  if (k === 'FDATE') return 'date'
  if (k === 'FCUSTOMERID' || k === 'FCUSTID') return 'custFid'
  if (k === 'FCUSTOMERID.FNAME' || k === 'FCUSTID.FNAME') return 'custName'
  if (k === 'FDOCUMENTSTATUS') return 'status'
  if (k === 'FCANCELSTATUS') return 'cancelled'
  if (k === 'FENTRYID' || k === 'FENTITY_FENTRYID') return 'entryId'
  if (k === 'FMATERIALID.FNUMBER') return 'matNo'
  if (k === 'FMATERIALID.FNAME') return 'matName'
  if (k.endsWith('.FNAME') && k.includes('UNIT')) return 'unit'
  if (k === 'FREALQTY' || k === 'FQTY' || k === 'FSALQTY') return 'qty'
  if (k === 'FTAXPRICE' || k === 'FPRICE') return 'price'
  if (k === 'FALLAMOUNT' || k === 'FAMOUNT' || k === 'FTAXAMOUNT') return 'amount'
  if (k === 'FSRCBILLNO') return 'srcNo'
  if (k === 'FENTRYNOTE' || k === 'FNOTE') return 'remark'
  if (poField && t === poField) return 'po'
  return 'other'
}

function num(v: unknown): number {
  return cellNumber(v)
}

/** 抓取出库分录；字段集逐套尝试，成功即用。整表重建 outbound_line */
async function syncOutboundLines(
  sinceDate: string
): Promise<{ lineCount: number; fieldKeys: string; zeroAmount: boolean; firstRowTypes: string }> {
  const client = getKingdee()
  const db = getDb()
  const mapping = loadOutstockMapping()
  const poField = (mapping.customerPoField || '').trim()

  const candidates = buildLineCandidates(mapping)
  const filter = sinceDate ? `FDate>='${sinceDate}'` : ''
  let lastErr: unknown = null

  for (let ci = 0; ci < candidates.length; ci++) {
    const fieldKeys = candidates[ci]
    const hasNextCandidate = ci < candidates.length - 1
    try {
      const kinds = fieldKeys.split(',').map((t) => classifyLineToken(t, poField))
      const idx = (kind: LineCol) => kinds.indexOf(kind)

      // 每套字段集尝试前先清掉本批次范围的旧行，保证可重入（增量同步只清 sinceDate 之后的单）
      if (sinceDate) {
        db.prepare(
          `DELETE FROM outbound_line WHERE outbound_fid IN (SELECT fid FROM outbound WHERE fdate >= ?)`
        ).run(sinceDate)
      } else {
        db.prepare(`DELETE FROM outbound_line`).run()
      }

      const insert = db.prepare(`
        INSERT INTO outbound_line(
          outbound_fid, material_number, material_name, unit,
          qty_milli, price_cents, amount_cents, src_order_no, customer_po, remark
        ) VALUES(
          @outbound_fid, @material_number, @material_name, @unit,
          @qty_milli, @price_cents, @amount_cents, @src_order_no, @customer_po, @remark
        )
      `)

      let start = 0
      let total = 0
      let amountSum = 0
      let firstRowTypes = ''
      let degrade = false
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const rows = await client.executeBillQuery({
          FormId: 'SAL_OUTSTOCK',
          FieldKeys: fieldKeys,
          FilterString: filter,
          OrderString: 'FDate ASC',
          StartRow: start,
          Limit: 2000
        })

        const tx = db.transaction((batch: unknown[][]) => {
          for (const raw of batch) {
            const r = asRow(raw)
            const outboundFid = idx('fid') >= 0 ? num(r[idx('fid')]) : 0
            if (!outboundFid) continue
            const qty = idx('qty') >= 0 ? num(r[idx('qty')]) : 0
            const price = idx('price') >= 0 ? num(r[idx('price')]) : 0
            const amount = idx('amount') >= 0 ? num(r[idx('amount')]) : 0
            insert.run({
              outbound_fid: outboundFid,
              material_number: idx('matNo') >= 0 ? cellScalar(r[idx('matNo')]) : '',
              material_name: idx('matName') >= 0 ? cellScalar(r[idx('matName')]) : '',
              unit: idx('unit') >= 0 ? cellScalar(r[idx('unit')]) : '',
              qty_milli: Math.round(qty * 1000),
              price_cents: Math.round(price * 100),
              amount_cents: Math.round(amount * 100),
              src_order_no: idx('srcNo') >= 0 ? cellScalar(r[idx('srcNo')]) : '',
              customer_po: idx('po') >= 0 ? cellScalar(r[idx('po')]) : '',
              remark: idx('remark') >= 0 ? cellScalar(r[idx('remark')]) : ''
            })
          }
        })
        tx(rows)
        total += rows.length
        if (rows.length) {
          const row = asRow(rows[0])
          amountSum += Math.round((idx('amount') >= 0 ? num(row[idx('amount')]) : 0) * 100)
          if (!firstRowTypes) firstRowTypes = row.map((c) => cellTypeName(c)).join('|')
          // 数量/单价/金额必须真是数字，否则这套字段集没意义，换下一套
          if (start === 0 && hasNextCandidate) {
            const numeric = (['qty', 'price', 'amount'] as LineCol[]).some(
              (k) => idx(k) >= 0 && typeof row[idx(k)] === 'number'
            )
            if (!numeric) {
              degrade = true
              break
            }
          }
        }
        if (rows.length < 2000) break
        start += 2000
      }

      if (degrade) continue

      // 行金额汇总回填表头金额（仅回填有分录的单）
      db.exec(`
        UPDATE outbound SET amount_cents = (
          SELECT CAST(ROUND(COALESCE(SUM(l.amount_cents), 0)) AS INTEGER)
          FROM outbound_line l WHERE l.outbound_fid = outbound.fid
        )
        WHERE EXISTS (SELECT 1 FROM outbound_line l WHERE l.outbound_fid = outbound.fid)
      `)

      return {
        lineCount: total,
        fieldKeys,
        zeroAmount: total > 0 && amountSum === 0,
        firstRowTypes
      }
    } catch (e) {
      lastErr = e
    }
  }
  throw lastErr instanceof Error ? lastErr : new Error(String(lastErr))
}

async function syncOutbound(sinceDate: string): Promise<SyncProgress> {
  const client = getKingdee()
  const db = getDb()
  const started = nowIso()
  db.prepare(
    `INSERT INTO sync_run(entity, status, row_count, message, started_at) VALUES(?,?,?,?,?)`
  ).run('outbound', 'running', 0, '', started)

  try {
    const filter = sinceDate ? `FDate>='${sinceDate}'` : ''
    const fieldKeys =
      'FID,FBillNo,FDate,FCustomerID,FCustomerID.FName,FDocumentStatus,FCancelStatus'
    let start = 0
    let total = 0
    const upsert = db.prepare(`
      INSERT INTO outbound(fid, fbillno, fdate, customer_fid, customer_name, amount_cents, status, cancelled, sync_at)
      VALUES(@fid, @fbillno, @fdate, @customer_fid, @customer_name, 0, @status, @cancelled, @sync_at)
      ON CONFLICT(fid) DO UPDATE SET
        fbillno=excluded.fbillno,
        fdate=excluded.fdate,
        customer_fid=excluded.customer_fid,
        customer_name=excluded.customer_name,
        status=excluded.status,
        cancelled=excluded.cancelled,
        sync_at=excluded.sync_at
    `)

    while (true) {
      const rows = await client.executeBillQuery({
        FormId: 'SAL_OUTSTOCK',
        FieldKeys: fieldKeys,
        FilterString: filter,
        OrderString: 'FDate ASC',
        StartRow: start,
        Limit: 2000
      })
      const tx = db.transaction((batch: unknown[][]) => {
        for (const raw of batch) {
          const r = asRow(raw)
          upsert.run({
            fid: cellNumber(r[0]),
            fbillno: cellScalar(r[1]),
            fdate: cellScalar(r[2]).slice(0, 10),
            customer_fid: cellNumber(r[3]),
            customer_name: cellScalar(r[4]),
            status: cellScalar(r[5]),
            cancelled: cellScalar(r[6]) === 'B' ? 1 : 0,
            sync_at: nowIso()
          })
        }
      })
      tx(rows)
      total += rows.length
      if (rows.length < 2000) break
      start += 2000
    }

    // 表头完成后同步分录；分录失败不回滚表头，但要在结果里报出来
    let lineMsg = ''
    try {
      const lines = await syncOutboundLines(sinceDate)
      lineMsg = `，分录 ${lines.lineCount} 条`
      if (lines.zeroAmount) {
        lineMsg += `（警告：金额全为 0，字段映射可能不对；首行类型 ${lines.firstRowTypes}）`
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      lineMsg = `，分录同步失败：${msg}`
    }

    const finished = nowIso()
    db.prepare(
      `UPDATE sync_run SET status=?, row_count=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run('success', total, `出库头 ${total} 条${lineMsg}`, finished, 'outbound', started)

    return {
      entity: 'outbound',
      status: 'success',
      rowCount: total,
      message: `出库头 ${total} 条${lineMsg}`,
      finishedAt: finished
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    const status: SyncProgress['status'] =
      /权限|permission|denied|无权限/i.test(msg) ? 'skipped_no_perm' : 'failed'
    db.prepare(
      `UPDATE sync_run SET status=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run(status, msg, nowIso(), 'outbound', started)
    return { entity: 'outbound', status, rowCount: 0, message: msg, finishedAt: nowIso() }
  }
}

async function syncSaleOrders(sinceDate: string): Promise<SyncProgress> {
  const client = getKingdee()
  const db = getDb()
  const started = nowIso()
  db.prepare(
    `INSERT INTO sync_run(entity, status, row_count, message, started_at) VALUES(?,?,?,?,?)`
  ).run('sale_order', 'running', 0, '', started)

  try {
    const filter = sinceDate ? `FDate>='${sinceDate}'` : ''
    const fieldKeys = 'FID,FBillNo,FDate,FCustId,FCustId.FName,FDocumentStatus'
    let start = 0
    let total = 0
    const upsert = db.prepare(`
      INSERT INTO sale_order(fid, fbillno, fdate, customer_fid, customer_name, amount_cents, status, sync_at)
      VALUES(@fid, @fbillno, @fdate, @customer_fid, @customer_name, 0, @status, @sync_at)
      ON CONFLICT(fid) DO UPDATE SET
        fbillno=excluded.fbillno,
        fdate=excluded.fdate,
        customer_fid=excluded.customer_fid,
        customer_name=excluded.customer_name,
        status=excluded.status,
        sync_at=excluded.sync_at
    `)

    while (true) {
      const rows = await client.executeBillQuery({
        FormId: 'SAL_SaleOrder',
        FieldKeys: fieldKeys,
        FilterString: filter,
        OrderString: 'FDate ASC',
        StartRow: start,
        Limit: 2000
      })
      const tx = db.transaction((batch: unknown[][]) => {
        for (const raw of batch) {
          const r = asRow(raw)
          upsert.run({
            fid: cellNumber(r[0]),
            fbillno: cellScalar(r[1]),
            fdate: cellScalar(r[2]).slice(0, 10),
            customer_fid: cellNumber(r[3]),
            customer_name: cellScalar(r[4]),
            status: cellScalar(r[5]),
            sync_at: nowIso()
          })
        }
      })
      tx(rows)
      total += rows.length
      if (rows.length < 2000) break
      start += 2000
    }

    const finished = nowIso()
    db.prepare(
      `UPDATE sync_run SET status=?, row_count=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run('success', total, 'ok', finished, 'sale_order', started)

    return {
      entity: 'sale_order',
      status: 'success',
      rowCount: total,
      message: `订单头 ${total} 条`,
      finishedAt: finished
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    const status: SyncProgress['status'] =
      /权限|permission|denied|无权限/i.test(msg) ? 'skipped_no_perm' : 'failed'
    db.prepare(
      `UPDATE sync_run SET status=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run(status, msg, nowIso(), 'sale_order', started)
    return { entity: 'sale_order', status, rowCount: 0, message: msg, finishedAt: nowIso() }
  }
}

export async function runSync(input: SyncStartInput): Promise<SyncProgress[]> {
  if (!tryAcquire('sync')) {
    throw new Error('已有同步或其它任务进行中')
  }
  const results: SyncProgress[] = []
  try {
    const login = await getKingdee().login()
    if (!login.ok) throw new Error(login.message)

    const order: SyncEntity[] = ['customer', 'sale_order', 'outbound']
    for (const entity of order) {
      emit({ entity, status: 'running', rowCount: 0, message: '同步中…' })
      let progress: SyncProgress
      if (entity === 'customer') progress = await syncCustomers(input.sinceDate)
      else if (entity === 'sale_order') progress = await syncSaleOrders(input.sinceDate)
      else progress = await syncOutbound(input.sinceDate)
      results.push(progress)
      emit(progress)
    }
    return results
  } finally {
    release('sync')
  }
}

export function listCustomers() {
  const db = getDb()
  return db
    .prepare(
      `SELECT fid, fnumber, fname, address FROM customer WHERE disabled = 0 ORDER BY fname COLLATE NOCASE`
    )
    .all()
}

/** 每个实体最近一次成功同步的统计，用于跨会话展示“上次同步” */
export function lastSyncStats(): LastSyncStat[] {
  const db = getDb()
  const rows = db
    .prepare(
      `
      SELECT entity, status, row_count AS rowCount, message, finished_at AS finishedAt
      FROM sync_run
      WHERE id IN (
        SELECT MAX(id) FROM sync_run
        WHERE status = 'success'
        GROUP BY entity
      )
    `
    )
    .all() as Array<{
    entity: string
    status: string
    rowCount: number
    message: string
    finishedAt: string | null
  }>

  const order: SyncEntity[] = ['customer', 'sale_order', 'outbound']
  return rows
    .map((r) => ({
      entity: r.entity as SyncEntity,
      status: r.status as LastSyncStat['status'],
      rowCount: Number(r.rowCount) || 0,
      message: String(r.message || ''),
      finishedAt: r.finishedAt
    }))
    .sort((a, b) => order.indexOf(a.entity) - order.indexOf(b.entity))
}
