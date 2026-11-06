import { BrowserWindow } from 'electron'
import { getDb } from '../db'
import { getPublicConfig } from '../configStore'
import { getKingdee } from '../kingdee/client'
import { buildLineCandidates } from '../kingdee/lineKeys'
import { asRow, cellNumber, cellScalar, cellTypeName } from '../kingdee/cells'
import { loadOutstockMapping } from '../mappingStore'
import { serverLineFieldKeys } from '../nova/tasks'
import { tryAcquire, release } from '../businessLock'
import { decideLinePull, decideSyncFilter, nextModifyWatermark, maxKdTime, normalizeKdTime } from './filter'
import {
  customerContentEqual,
  headerNeedsLineRebuild,
  outboundContentEqual,
  resolveSyncAt
} from './stamp'
import type {
  LastSyncStat,
  SyncEntity,
  SyncProgress,
  SyncRunRecord,
  SyncStartInput,
  SyncTrigger
} from '../../shared/types'

function emit(progress: SyncProgress) {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('sync:progress', progress)
  }
}

function nowIso() {
  return new Date().toISOString()
}

/** 读 meta 表里的同步策略键（水位 / 探测结论，均按账套隔离） */
function getMeta(key: string): string | undefined {
  const row = getDb().prepare(`SELECT value FROM meta WHERE key = ?`).get(key) as
    | { value: string }
    | undefined
  return row ? row.value : undefined
}

/** 写 meta 表（upsert） */
function setMeta(key: string, value: string) {
  getDb()
    .prepare(
      `INSERT INTO meta(key, value) VALUES(?, ?)
       ON CONFLICT(key) DO UPDATE SET value = excluded.value`
    )
    .run(key, value)
}

type HeadFetch = { total: number; maxModify: string }

/** 按账套探测 FModifyDate：8 字段失败且 7 字段成功 = 不可用；都失败抛第一次错误 */
async function fetchWithModifyProbe(
  supportedKey: string,
  baseKeys: string,
  fetch: (fieldKeys: string) => Promise<HeadFetch>
): Promise<HeadFetch> {
  const supported = getMeta(supportedKey)
  if (supported === '1') {
    return fetch(`${baseKeys},FModifyDate`)
  }
  if (supported === '0') {
    return fetch(baseKeys)
  }
  try {
    const head = await fetch(`${baseKeys},FModifyDate`)
    setMeta(supportedKey, '1')
    return head
  } catch (firstErr) {
    try {
      const head = await fetch(baseKeys)
      setMeta(supportedKey, '0')
      return head
    } catch {
      throw firstErr
    }
  }
}

function persistModifyWatermark(
  key: string,
  mode: 'fdate' | 'fmodify' | 'full',
  maxModify: string
) {
  const next = nextModifyWatermark({
    mode,
    existingWatermark: getMeta(key),
    maxModify
  })
  if (next) setMeta(key, next)
}

async function syncCustomers(_sinceDate: string, trigger: SyncTrigger): Promise<SyncProgress> {
  const client = getKingdee()
  const db = getDb()
  const started = nowIso()
  db.prepare(
    `INSERT INTO sync_run(entity, status, row_count, message, started_at, source) VALUES(?,?,?,?,?,?)`
  ).run('customer', 'running', 0, '', started, trigger)

  try {
    const acctId = getPublicConfig().acctId || ''
    const supportedKey = `customer_modify_supported:${acctId}`
    const watermarkKey = `last_customer_modify_watermark:${acctId}`
    const decision = decideSyncFilter({
      supportsModify: getMeta(supportedKey) === '1',
      watermark: getMeta(watermarkKey),
      enableSweep: false
    })
    const filter = decision.filter

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
    const selectPrev = db.prepare(
      `SELECT fid, fnumber, fname, address, disabled, sync_at FROM customer WHERE fid = ?`
    )

    const fetchCustomers = async (fieldKeys: string): Promise<HeadFetch> => {
      const hasModify = fieldKeys.split(',').includes('FModifyDate')
      let start = 0
      let count = 0
      let modifyMax = ''
      while (true) {
        const rows = await client.executeBillQuery({
          FormId: 'BD_Customer',
          FieldKeys: fieldKeys,
          FilterString: filter,
          StartRow: start,
          Limit: 2000
        })
        const stamp = nowIso()
        const tx = db.transaction((batch: unknown[][]) => {
          for (const raw of batch) {
            const r = asRow(raw)
            const incoming = {
              fid: cellNumber(r[0]),
              fnumber: cellScalar(r[1]),
              fname: cellScalar(r[2]),
              address: cellScalar(r[3]),
              disabled: cellScalar(r[4]) === 'B' ? 1 : 0
            }
            const prev = selectPrev.get(incoming.fid) as
              | {
                  fid: number
                  fnumber: string | null
                  fname: string
                  address: string | null
                  disabled: number
                  sync_at: string | null
                }
              | undefined
            const unchanged = !!(prev && customerContentEqual(prev, incoming))
            upsert.run({
              ...incoming,
              sync_at: resolveSyncAt(unchanged, prev?.sync_at, stamp)
            })
            if (hasModify) modifyMax = maxKdTime(modifyMax, normalizeKdTime(r[5]))
          }
        })
        tx(rows)
        count += rows.length
        if (rows.length < 2000) break
        start += 2000
      }
      return { total: count, maxModify: modifyMax }
    }

    const head = await fetchWithModifyProbe(
      supportedKey,
      'FCUSTID,FNumber,FName,FADDRESS,FForbidStatus',
      fetchCustomers
    )
    persistModifyWatermark(watermarkKey, decision.mode, head.maxModify)

    const modeMsg =
      decision.mode === 'fmodify'
        ? `（增量，水位 ${decision.watermark}）`
        : decision.mode === 'full'
          ? '（全量）'
          : ''
    const finished = nowIso()
    const message = `客户 ${head.total} 条${modeMsg}`
    db.prepare(
      `UPDATE sync_run SET status=?, row_count=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run('success', head.total, message, finished, 'customer', started)

    return {
      entity: 'customer',
      status: 'success',
      rowCount: head.total,
      message,
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
  | 'entryId' | 'matNo' | 'matName' | 'spec' | 'unit' | 'qty' | 'price' | 'amount'
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
  if (k === 'FMATERIALID.FSPECIFICATION' || k === 'FSPECIFICATION') return 'spec'
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

/** 抓取出库分录；字段集逐套尝试，成功即用。重建范围 = 本轮业务内容变化的表头 */
async function syncOutboundLines(
  filter: string,
  changedFids: number[]
): Promise<{ lineCount: number; fieldKeys: string; zeroAmount: boolean; firstRowTypes: string }> {
  const plan = decideLinePull(filter, changedFids)
  if (plan.action === 'skip') {
    return { lineCount: 0, fieldKeys: '', zeroAmount: false, firstRowTypes: '' }
  }
  const changedSet = new Set(changedFids)
  const client = getKingdee()
  const db = getDb()
  const mapping = loadOutstockMapping()
  const poField = (mapping.customerPoField || '').trim()

  // 服务端配置作为候选之一（运维改字段不用发版），失败仍按后续候选降级
  const candidates = buildLineCandidates(mapping, serverLineFieldKeys())
  let lastErr: unknown = null

  for (let ci = 0; ci < candidates.length; ci++) {
    const fieldKeys = candidates[ci]
    const hasNextCandidate = ci < candidates.length - 1
    try {
      const kinds = fieldKeys.split(',').map((t) => classifyLineToken(t, poField))
      const idx = (kind: LineCol) => kinds.indexOf(kind)

      // 只删内容变化的表头的分录。月度回看会把未改的单再拉回来，那些单不得重建。
      const del = db.prepare(`DELETE FROM outbound_line WHERE outbound_fid = ?`)
      db.transaction(() => {
        for (const fid of changedFids) del.run(fid)
      })()

      const insert = db.prepare(`
        INSERT INTO outbound_line(
          outbound_fid, entry_id, material_number, material_name, spec, unit,
          qty_milli, price_cents, amount_cents, src_order_no, customer_po, remark, sync_at
        ) VALUES(
          @outbound_fid, @entry_id, @material_number, @material_name, @spec, @unit,
          @qty_milli, @price_cents, @amount_cents, @src_order_no, @customer_po, @remark, @sync_at
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
          FilterString: plan.filter,
          OrderString: 'FDate ASC',
          StartRow: start,
          Limit: 2000
        })

        const stamp = nowIso()
        const tx = db.transaction((batch: unknown[][]) => {
          for (const raw of batch) {
            const r = asRow(raw)
            const outboundFid = idx('fid') >= 0 ? num(r[idx('fid')]) : 0
            if (!outboundFid || !changedSet.has(outboundFid)) continue
            const qty = idx('qty') >= 0 ? num(r[idx('qty')]) : 0
            const price = idx('price') >= 0 ? num(r[idx('price')]) : 0
            const amount = idx('amount') >= 0 ? num(r[idx('amount')]) : 0
            const rawEntryId = idx('entryId') >= 0 ? num(r[idx('entryId')]) : 0
            const amountCents = Math.round(amount * 100)
            insert.run({
              outbound_fid: outboundFid,
              // 分录内码可能缺失：留空由服务端按「单据 + 行序号」生成稳定键
              entry_id: rawEntryId > 0 ? rawEntryId : null,
              material_number: idx('matNo') >= 0 ? cellScalar(r[idx('matNo')]) : '',
              material_name: idx('matName') >= 0 ? cellScalar(r[idx('matName')]) : '',
              spec: idx('spec') >= 0 ? cellScalar(r[idx('spec')]) : '',
              unit: idx('unit') >= 0 ? cellScalar(r[idx('unit')]) : '',
              qty_milli: Math.round(qty * 1000),
              price_cents: Math.round(price * 100),
              amount_cents: amountCents,
              src_order_no: idx('srcNo') >= 0 ? cellScalar(r[idx('srcNo')]) : '',
              customer_po: idx('po') >= 0 ? cellScalar(r[idx('po')]) : '',
              remark: idx('remark') >= 0 ? cellScalar(r[idx('remark')]) : '',
              sync_at: stamp
            })
            total += 1
            amountSum += amountCents
          }
        })
        tx(rows)
        if (rows.length) {
          const row = asRow(rows[0])
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

async function syncOutbound(sinceDate: string | undefined, trigger: SyncTrigger): Promise<SyncProgress> {
  const client = getKingdee()
  const db = getDb()
  const started = nowIso()
  db.prepare(
    `INSERT INTO sync_run(entity, status, row_count, message, started_at, source) VALUES(?,?,?,?,?,?)`
  ).run('outbound', 'running', 0, '', started, trigger)

  try {
    const acctId = getPublicConfig().acctId || ''
    // 双水位决策：显式日期 → FDate；有水位且账套支持 → FModifyDate 增量；否则全量
    const decision = decideSyncFilter({
      explicitSinceDate: sinceDate,
      supportsModify: getMeta(`outstock_modify_supported:${acctId}`) === '1',
      watermark: getMeta(`last_modify_watermark:${acctId}`),
      lastSweepAt: getMeta(`last_sweep_at:${acctId}`)
    })
    const filter = decision.filter
    const batchStartIso = nowIso()
    const watermarkKey = `last_modify_watermark:${acctId}`
    const changedFids: number[] = []
    const fidsWithLines = new Set(
      (
        db.prepare(`SELECT DISTINCT outbound_fid AS fid FROM outbound_line`).all() as Array<{
          fid: number
        }>
      ).map((r) => r.fid)
    )

    const upsert = db.prepare(`
      INSERT INTO outbound(fid, fbillno, fdate, customer_fid, customer_name, amount_cents, status, cancelled, modify_time, sync_at)
      VALUES(@fid, @fbillno, @fdate, @customer_fid, @customer_name, 0, @status, @cancelled, @modify_time, @sync_at)
      ON CONFLICT(fid) DO UPDATE SET
        fbillno=excluded.fbillno,
        fdate=excluded.fdate,
        customer_fid=excluded.customer_fid,
        customer_name=excluded.customer_name,
        status=excluded.status,
        cancelled=excluded.cancelled,
        modify_time=excluded.modify_time,
        sync_at=excluded.sync_at
    `)
    const selectPrev = db.prepare(
      `SELECT fid, fbillno, fdate, customer_fid, customer_name, status, cancelled, modify_time, sync_at
       FROM outbound WHERE fid = ?`
    )

    /** 拉表头并 upsert；未变行保留 sync_at。返回 { total, maxModify } */
    const fetchHead = async (fieldKeys: string): Promise<HeadFetch> => {
      changedFids.length = 0
      const hasModify = fieldKeys.split(',').includes('FModifyDate')
      let start = 0
      let count = 0
      let modifyMax = ''
      while (true) {
        const rows = await client.executeBillQuery({
          FormId: 'SAL_OUTSTOCK',
          FieldKeys: fieldKeys,
          FilterString: filter,
          OrderString: 'FDate ASC',
          StartRow: start,
          Limit: 2000
        })
        const stamp = nowIso()
        const tx = db.transaction((batch: unknown[][]) => {
          for (const raw of batch) {
            const r = asRow(raw)
            const fid = cellNumber(r[0])
            const prev = selectPrev.get(fid) as
              | {
                  fid: number
                  fbillno: string | null
                  fdate: string | null
                  customer_fid: number | null
                  customer_name: string | null
                  status: string | null
                  cancelled: number
                  modify_time: string | null
                  sync_at: string | null
                }
              | undefined
            const modify = hasModify ? normalizeKdTime(r[7]) : ''
            const incoming = {
              fid,
              fbillno: cellScalar(r[1]),
              fdate: cellScalar(r[2]).slice(0, 10),
              customer_fid: cellNumber(r[3]),
              customer_name: cellScalar(r[4]),
              status: cellScalar(r[5]),
              cancelled: cellScalar(r[6]) === 'B' ? 1 : 0,
              modify_time: hasModify ? modify || null : prev?.modify_time ?? null
            }
            const unchanged = !!(prev && outboundContentEqual(prev, incoming))
            if (
              headerNeedsLineRebuild({
                unchanged,
                existed: Boolean(prev),
                hasLines: fidsWithLines.has(fid)
              })
            ) {
              changedFids.push(fid)
            }
            upsert.run({
              ...incoming,
              sync_at: resolveSyncAt(unchanged, prev?.sync_at, stamp)
            })
            if (hasModify) modifyMax = maxKdTime(modifyMax, modify)
          }
        })
        tx(rows)
        count += rows.length
        if (rows.length < 2000) break
        start += 2000
      }
      return { total: count, maxModify: modifyMax }
    }

    const HEAD_BASE =
      'FID,FBillNo,FDate,FCustomerID,FCustomerID.FName,FDocumentStatus,FCancelStatus'
    const supportedKey = `outstock_modify_supported:${acctId}`
    const head = await fetchWithModifyProbe(supportedKey, HEAD_BASE, fetchHead)
    const total = head.total

    // 表头完成后同步分录；分录失败不回滚表头，也不推进金蝶水位（避免下次增量跳过未完成的分录）
    let lineMsg = ''
    let linesOk = true
    try {
      const lines = await syncOutboundLines(filter, changedFids)
      lineMsg =
        changedFids.length === 0 ? '，分录无变化' : `，分录 ${lines.lineCount} 条`
      if (lines.zeroAmount) {
        lineMsg += `（警告：金额全为 0，字段映射可能不对；首行类型 ${lines.firstRowTypes}）`
      }
    } catch (e) {
      linesOk = false
      const msg = e instanceof Error ? e.message : String(e)
      lineMsg = `，分录同步失败：${msg}`
    }

    if (linesOk) {
      persistModifyWatermark(watermarkKey, decision.mode, head.maxModify)
      if (decision.sweepFrom) {
        setMeta(`last_sweep_at:${acctId}`, batchStartIso)
      }
    }

    const modeMsg =
      decision.mode === 'fmodify'
        ? `（增量，水位 ${decision.watermark}${decision.sweepFrom ? `，含月度回看 ${decision.sweepFrom}` : ''}）`
        : decision.mode === 'fdate'
          ? `（按业务日期 ${sinceDate} 重拉）`
          : '（全量）'
    const finished = nowIso()
    db.prepare(
      `UPDATE sync_run SET status=?, row_count=?, message=?, finished_at=? WHERE entity=? AND started_at=?`
    ).run('success', total, `出库头 ${total} 条${modeMsg}${lineMsg}`, finished, 'outbound', started)

    return {
      entity: 'outbound',
      status: 'success',
      rowCount: total,
      message: `出库头 ${total} 条${modeMsg}${lineMsg}`,
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

async function syncSaleOrders(sinceDate: string, trigger: SyncTrigger): Promise<SyncProgress> {
  const client = getKingdee()
  const db = getDb()
  const started = nowIso()
  db.prepare(
    `INSERT INTO sync_run(entity, status, row_count, message, started_at, source) VALUES(?,?,?,?,?,?)`
  ).run('sale_order', 'running', 0, '', started, trigger)

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
  const trigger: SyncTrigger = input.trigger || 'manual'
  const results: SyncProgress[] = []
  try {
    const login = await getKingdee().login()
    if (!login.ok) throw new Error(login.message)

    const order: SyncEntity[] = ['customer', 'sale_order', 'outbound']
    for (const entity of order) {
      emit({ entity, status: 'running', rowCount: 0, message: '同步中…' })
      let progress: SyncProgress
      if (entity === 'customer') progress = await syncCustomers(input.sinceDate || '', trigger)
      else if (entity === 'sale_order') progress = await syncSaleOrders(input.sinceDate || '', trigger)
      else progress = await syncOutbound(input.sinceDate, trigger)
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

/**
 * 同步历史（含失败与定时 / 指令触发），新到旧。
 *
 * <p>「进行中」行通常是崩溃残留——保留展示，让断档现场可见。
 */
export function syncHistory(limit = 100): SyncRunRecord[] {
  const db = getDb()
  const rows = db
    .prepare(
      `
      SELECT id, entity, status, row_count AS rowCount, message,
             started_at AS startedAt, finished_at AS finishedAt, source
      FROM sync_run
      ORDER BY id DESC
      LIMIT ?
    `
    )
    .all(Math.max(1, Math.min(500, limit))) as Array<{
    id: number
    entity: string
    status: string
    rowCount: number
    message: string
    startedAt: string | null
    finishedAt: string | null
    source: string | null
  }>

  return rows.map((r) => ({
    id: r.id,
    entity: r.entity,
    status: r.status as SyncRunRecord['status'],
    rowCount: Number(r.rowCount) || 0,
    message: String(r.message || ''),
    startedAt: r.startedAt,
    finishedAt: r.finishedAt,
    source: (r.source as SyncTrigger | null) || null
  }))
}
