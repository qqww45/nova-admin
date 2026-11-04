import { app, BrowserWindow } from 'electron'
import { hostname } from 'os'
import { randomUUID } from 'crypto'
import { getDb } from '../db'
import {
  clearNovaBinding,
  getNovaRuntime,
  getNovaState,
  getPublicConfig,
  saveNovaBinding,
  saveNovaUploadResult
} from '../configStore'
import {
  UPLOAD_CHUNK,
  finishBatch,
  formatLocalTime,
  redeemBinding,
  startBatch,
  uploadCustomers,
  uploadLines,
  uploadOutbounds
} from './client'
import type {
  NovaBindInput,
  NovaBindResult,
  NovaState,
  NovaUploadRecord,
  NovaUploadResult
} from '../../shared/types'

/**
 * 采集器上行同步。
 *
 * <p>与本地同步的关系（方案 §4）：本地仍是主，上行是"尽力而为的双写"。
 * 因此这里的所有异常都被吞成结果对象——上行失败绝不能让业务员拿不到对账单。
 *
 * <p>幂等策略：同一批数据重跑安全（服务端按金蝶内码 upsert）。
 * 只有服务端返回 200 才推进本地水位，断电 / 断网后下次自动重传。
 */

interface CustomerRow {
  fid: number
  fnumber: string | null
  fname: string
  address: string | null
  disabled: number
  sync_at: string | null
}

interface OutboundRow {
  fid: number
  fbillno: string | null
  fdate: string | null
  customer_fid: number | null
  customer_name: string | null
  status: string | null
  cancelled: number
  amount_cents: number
  /** 金蝶 FModifyDate（双水位上线后有值；老数据为空） */
  modify_time: string | null
  sync_at: string | null
}

interface LineRow {
  outbound_fid: number
  entry_id: number | null
  material_number: string | null
  material_name: string | null
  spec: string | null
  unit: string | null
  qty_milli: number
  price_cents: number
  amount_cents: number
  src_order_no: string | null
  customer_po: string | null
  remark: string | null
  sync_at: string | null
}

/** 本地待上报行数（用于页面提示） */
export function pendingUploadRows(): number {
  const db = getDb()
  const since = getNovaRuntime().uploadWatermark || ''
  const one = (sql: string) => {
    const row = db.prepare(sql).get(since) as { n: number } | undefined
    return Number(row?.n) || 0
  }
  return (
    one(`SELECT COUNT(*) AS n FROM customer WHERE sync_at IS NOT NULL AND sync_at > ?`) +
    one(`SELECT COUNT(*) AS n FROM outbound WHERE sync_at IS NOT NULL AND sync_at > ?`) +
    one(`SELECT COUNT(*) AS n FROM outbound_line WHERE sync_at IS NOT NULL AND sync_at > ?`)
  )
}

/** 状态 + 待上报行数（渲染进程展示用） */
export function novaState(): NovaState {
  try {
    return { ...getNovaState(), pendingRows: pendingUploadRows() }
  } catch {
    return getNovaState()
  }
}

/** 上行结果广播到所有窗口：上行有四个入口（手动 / 同步后自动 / 托盘 / 指令），统一在这里广播 */
function broadcastNova(result: NovaUploadResult) {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('nova:upload', result)
  }
}

/** 一次上行落到 nova_upload_run（历史展示用；noop 也记，证明"定时同步确实在跑"） */
function recordUploadRun(row: {
  batchId?: string
  status: 'success' | 'failed' | 'noop'
  message: string
  received: number
  upserted: number
  skipped: number
  startedAt: string
  finishedAt: string
}) {
  try {
    getDb()
      .prepare(
        `INSERT INTO nova_upload_run(batch_id, status, message, received, upserted, skipped, started_at, finished_at)
         VALUES(?,?,?,?,?,?,?,?)`
      )
      .run(
        row.batchId ?? null,
        row.status,
        row.message.slice(0, 500),
        row.received,
        row.upserted,
        row.skipped,
        row.startedAt,
        row.finishedAt
      )
  } catch {
    /* 历史落库失败不影响主流程 */
  }
}

/** 上行历史（新到旧） */
export function novaUploadHistory(limit = 100): NovaUploadRecord[] {
  const rows = getDb()
    .prepare(
      `
      SELECT id, batch_id AS batchId, status, message,
             received, upserted, skipped,
             started_at AS startedAt, finished_at AS finishedAt
      FROM nova_upload_run
      ORDER BY id DESC
      LIMIT ?
    `
    )
    .all(Math.max(1, Math.min(500, limit))) as Array<{
    id: number
    batchId: string | null
    status: string
    message: string
    received: number
    upserted: number
    skipped: number
    startedAt: string | null
    finishedAt: string | null
  }>

  return rows.map((r) => ({
    id: r.id,
    batchId: r.batchId,
    status: r.status as NovaUploadRecord['status'],
    message: String(r.message || ''),
    received: Number(r.received) || 0,
    upserted: Number(r.upserted) || 0,
    skipped: Number(r.skipped) || 0,
    startedAt: r.startedAt,
    finishedAt: r.finishedAt
  }))
}

/** 绑定：用 nova 页面生成的 8 位绑定码换取机器令牌 */
export async function bindNova(input: NovaBindInput): Promise<NovaBindResult> {
  const cfg = getPublicConfig()
  const baseUrl = (input.baseUrl || getNovaRuntime().baseUrl).trim()
  const code = (input.code || '').replace(/\s+/g, '').toUpperCase()

  if (!/^https?:\/\//i.test(baseUrl)) {
    return { ok: false, message: '对账平台地址需以 http:// 或 https:// 开头', state: novaState() }
  }
  if (code.length < 6) {
    return { ok: false, message: '请输入网页上生成的绑定码', state: novaState() }
  }
  if (!cfg.acctId) {
    return { ok: false, message: '请先在「连接金蝶云」里填写并保存数据中心', state: novaState() }
  }

  try {
    const result = await redeemBinding(baseUrl, {
      code,
      acctId: cfg.acctId,
      acctName: cfg.acctName,
      kdUsername: cfg.username,
      kdServerUrl: cfg.serverUrl,
      machineName: hostname(),
      clientVersion: app.getVersion()
    })
    const state = saveNovaBinding({
      baseUrl,
      token: result.token,
      bindingId: result.bindingId,
      userId: result.userId,
      acctId: result.acctId,
      acctName: result.acctName
    })
    return {
      ok: true,
      message: `已绑定账套 ${result.acctName || result.acctId}，后续同步会自动上报`,
      state: { ...state, pendingRows: 0 }
    }
  } catch (e) {
    return { ok: false, message: e instanceof Error ? e.message : String(e), state: novaState() }
  }
}

/** 解除本机绑定（服务端令牌仍需在网页上吊销） */
export function unbindNova(): NovaState {
  return clearNovaBinding()
}

/**
 * 执行一次上行同步（并发去重）。
 *
 * <p>入口有四个：本地同步完成自动上行、指令 sync.now、托盘定时/手动、页面手动点。
 * 这些入口共用"本地待上报行"这一个数据集，重叠执行时两个批次会同时 upsert 同一批行：
 * 读-判-写不是原子的，后落库方撞唯一键 / SQLite 撞写锁，服务端只能回 500
 *（2026-11-03 生产小批次的「服务器内部错误」与该形态吻合：整批数据因规格校验
 * 反复失败后积压，每次触发都重传同一批行，重叠概率随之放大）。
 *
 * <p>因此在进程内做去重：已有一次上行在跑时，后来的调用直接复用同一次结果，
 * 不再并发发起第二个批次。上传本身是幂等的（服务端按金蝶内码 upsert），
 * 跳过一次重复触发不影响正确性。
 *
 * @param entity 预留：限定实体；不传表示客户 + 出库单 + 分录全量增量
 */
let uploadInFlight: Promise<NovaUploadResult> | null = null

export function uploadToNova(): Promise<NovaUploadResult> {
  if (uploadInFlight) return uploadInFlight
  uploadInFlight = doUploadToNova().finally(() => {
    uploadInFlight = null
  })
  return uploadInFlight
}

async function doUploadToNova(): Promise<NovaUploadResult> {
  const rt = getNovaRuntime()
  if (!rt.token || !rt.bindingId) {
    const result = { ok: false as const, status: 'unbound' as const, message: '尚未绑定对账平台' }
    broadcastNova(result)
    return result
  }
  const db = getDb()
  const since = rt.uploadWatermark || ''
  const startedAt = formatLocalTime()
  const startedMs = Date.now()

  const customers = db
    .prepare(
      `SELECT fid, fnumber, fname, address, disabled, sync_at
       FROM customer WHERE sync_at IS NOT NULL AND sync_at > ? ORDER BY sync_at`
    )
    .all(since) as CustomerRow[]
  const outbounds = db
    .prepare(
      `SELECT fid, fbillno, fdate, customer_fid, customer_name, status, cancelled, amount_cents, modify_time, sync_at
       FROM outbound WHERE sync_at IS NOT NULL AND sync_at > ? ORDER BY sync_at`
    )
    .all(since) as OutboundRow[]
  const lines = db
    .prepare(
      `SELECT outbound_fid, entry_id, material_number, material_name, spec, unit,
              qty_milli, price_cents, amount_cents, src_order_no, customer_po, remark, sync_at
       FROM outbound_line WHERE sync_at IS NOT NULL AND sync_at > ?
       ORDER BY outbound_fid, id`
    )
    .all(since) as LineRow[]

  if (!customers.length && !outbounds.length && !lines.length) {
    const finishedAt = formatLocalTime()
    const message = '没有需要上报的新数据'
    recordUploadRun({
      status: 'noop',
      message,
      received: 0,
      upserted: 0,
      skipped: 0,
      startedAt,
      finishedAt
    })
    const result = { ok: true as const, status: 'noop' as const, message, finishedAt }
    broadcastNova(result)
    return result
  }

  // 水位只取"本次真正读到的最大 sync_at"，保证不跳过任何一行
  const watermark = maxSyncAt([
    ...customers.map((r) => r.sync_at),
    ...outbounds.map((r) => r.sync_at),
    ...lines.map((r) => r.sync_at)
  ])

  const batchId = randomUUID()
  let received = 0
  let upserted = 0
  let skipped = 0

  try {
    await startBatch(rt.baseUrl, rt.token, {
      batchId,
      entity: 'ALL',
      mode: 'INCREMENTAL',
      clientVersion: app.getVersion()
    })

    for (const chunk of chunked(customers, UPLOAD_CHUNK)) {
      const r = await uploadCustomers(rt.baseUrl, rt.token, {
        batchId,
        rows: chunk.map((row) => ({
          kdFid: row.fid,
          number: row.fnumber || '',
          name: row.fname || '',
          address: row.address || '',
          disabled: row.disabled ? 1 : 0
        }))
      })
      received += r.received
      upserted += r.upserted
      skipped += r.skipped
    }

    for (const chunk of chunked(outbounds, UPLOAD_CHUNK)) {
      const r = await uploadOutbounds(rt.baseUrl, rt.token, {
        batchId,
        rows: chunk.map((row) => ({
          fid: row.fid,
          billNo: row.fbillno || '',
          // 本地存的是 yyyy-MM-dd；补零成后端能解析的 LocalDateTime
          bizDate: row.fdate ? `${row.fdate.slice(0, 10)} 00:00:00` : undefined,
          customerFid: row.customer_fid ?? undefined,
          customerName: row.customer_name || '',
          docStatus: row.status || '',
          cancelStatus: row.cancelled ? 'B' : 'A',
          amountCents: Math.round(row.amount_cents || 0),
          // 金蝶 FModifyDate（服务端 kd_modify_time 的真实语义）；老数据退回本地写入时间
          modifyTime: row.modify_time ? toLocalTime(row.modify_time) : row.sync_at ? toLocalTime(row.sync_at) : undefined
        }))
      })
      received += r.received
      upserted += r.upserted
      skipped += r.skipped
    }

    // 分录内码兜底：按「单据 + 单内行序号」生成，必须在切块之前算好，
    // 否则分块后序号从 0 重新开始，会把同一张单的其它行覆盖成同一行
    const perBillSeq = new Map<number, number>()
    const lineEntryIds = lines.map((row) => {
      if (row.entry_id && row.entry_id > 0) return row.entry_id
      const seq = perBillSeq.get(row.outbound_fid) ?? 0
      perBillSeq.set(row.outbound_fid, seq + 1)
      return row.outbound_fid * 100_000 + seq
    })

    for (let offset = 0; offset < lines.length; offset += UPLOAD_CHUNK) {
      const chunk = lines.slice(offset, offset + UPLOAD_CHUNK)
      const r = await uploadLines(rt.baseUrl, rt.token, {
        batchId,
        rows: chunk.map((row, i) => ({
          outboundFid: row.outbound_fid,
          entryId: lineEntryIds[offset + i],
          materialNumber: row.material_number || '',
          materialName: row.material_name || '',
          spec: row.spec || '',
          unit: row.unit || '',
          qtyMilli: Math.round(row.qty_milli || 0),
          priceCents: Math.round(row.price_cents || 0),
          amountCents: Math.round(row.amount_cents || 0),
          srcOrderNo: row.src_order_no || '',
          customerPo: row.customer_po || '',
          remark: row.remark || ''
        }))
      })
      received += r.received
      upserted += r.upserted
      skipped += r.skipped
    }

    const finishedAt = formatLocalTime()
    await finishBatch(rt.baseUrl, rt.token, batchId, {
      status: 'SUCCESS',
      rowCount: received,
      upsertCount: upserted,
      skippedCount: skipped,
      // M1 水位语义：客户端完成本次上行的本地时间（服务端只用于展示与单调推进）；
      // 接入金蝶 FModifyDate 后改为真正的金蝶修改时间
      watermarkTo: watermark,
      startedAt,
      finishedAt,
      costMs: Date.now() - startedMs,
      message: `客户 ${customers.length}、出库 ${outbounds.length}、分录 ${lines.length}`
    })

    const message = `已上报 客户 ${customers.length} / 出库 ${outbounds.length} / 分录 ${lines.length}`
    saveNovaUploadResult({ ok: true, message, watermark, finishedAt })
    recordUploadRun({
      batchId,
      status: 'success',
      message,
      received,
      upserted,
      skipped,
      startedAt,
      finishedAt
    })
    const result: NovaUploadResult = {
      ok: true,
      status: 'success',
      message,
      received,
      upserted,
      skipped,
      finishedAt
    }
    broadcastNova(result)
    return result
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    // 失败也要回报批次，服务端才能看到失败现场；回报本身失败不影响主流程
    try {
      await finishBatch(rt.baseUrl, rt.token, batchId, {
        status: 'FAILED',
        rowCount: received,
        upsertCount: upserted,
        skippedCount: skipped,
        startedAt,
        finishedAt: formatLocalTime(),
        costMs: Date.now() - startedMs,
        message
      })
    } catch {
      /* 忽略：网络断了回报也发不出去 */
    }
    const finishedAt = formatLocalTime()
    saveNovaUploadResult({ ok: false, message, finishedAt })
    recordUploadRun({
      batchId,
      status: 'failed',
      message,
      received,
      upserted,
      skipped,
      startedAt,
      finishedAt
    })
    const result: NovaUploadResult = {
      ok: false,
      status: 'failed',
      message,
      received,
      upserted,
      skipped,
      finishedAt
    }
    broadcastNova(result)
    return result
  }
}

/** 按大小切块 */
function chunked<T>(rows: T[], size: number): T[][] {
  const result: T[][] = []
  for (let i = 0; i < rows.length; i += size) result.push(rows.slice(i, i + size))
  return result
}

/** 取最大 sync_at（ISO 字符串可直接比较） */
function maxSyncAt(values: Array<string | null>): string | undefined {
  let max: string | undefined
  for (const v of values) {
    if (v && (!max || v > max)) max = v
  }
  return max
}

/** ISO 时间 → 后端可解析的本地时间字符串 */
function toLocalTime(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? formatLocalTime() : formatLocalTime(d)
}
