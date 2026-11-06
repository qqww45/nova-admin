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
  saveNovaUploadProgress,
  saveNovaUploadResult
} from '../configStore'
import {
  chunkRows,
  finishBatch,
  formatLocalTime,
  redeemBinding,
  startBatch,
  uploadCustomers,
  uploadLines,
  uploadOutbounds
} from './client'
import { retryTransient, safeWatermarkAfterPrefix, uploadWithRetry } from './uploadResilience'
import type {
  NovaBindInput,
  NovaBindResult,
  NovaState,
  NovaUploadRecord,
  NovaUploadResult,
  SyncTrigger
} from '../../shared/types'

/**
 * 采集器上行同步。
 *
 * <p>与本地同步的关系（方案 §4）：本地仍是主，上行是"尽力而为的双写"。
 * 因此这里的所有异常都被吞成结果对象——上行失败绝不能让业务员拿不到对账单。
 *
 * <p>幂等策略：同一批数据重跑安全（服务端按金蝶内码 upsert）。
 * 每个实体在块（或半块）确认后立刻推进分实体水位；整批 524 / 超时不得回拨，
 * 下次只传尚未确认的行。524 / 非 JSON / 超时会有限重试并对半切块。
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
  id: number
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

function entitySince(
  rt: ReturnType<typeof getNovaRuntime>,
  key: 'uploadWatermarkCustomer' | 'uploadWatermarkOutbound' | 'uploadWatermarkLine'
): string {
  return (rt[key] || rt.uploadWatermark || '').trim()
}

/** 本地待上报行数（用于页面提示） */
export function pendingUploadRows(): number {
  const db = getDb()
  const rt = getNovaRuntime()
  const one = (sql: string, since: string) => {
    const row = db.prepare(sql).get(since) as { n: number } | undefined
    return Number(row?.n) || 0
  }
  const sql = (table: string) =>
    `SELECT COUNT(*) AS n FROM ${table} WHERE sync_at IS NOT NULL AND sync_at > ?`
  return (
    one(sql('customer'), entitySince(rt, 'uploadWatermarkCustomer')) +
    one(sql('outbound'), entitySince(rt, 'uploadWatermarkOutbound')) +
    one(sql('outbound_line'), entitySince(rt, 'uploadWatermarkLine'))
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

export function uploadToNova(opts?: { trigger?: SyncTrigger }): Promise<NovaUploadResult> {
  if (uploadInFlight) return uploadInFlight
  uploadInFlight = doUploadToNova(opts).finally(() => {
    uploadInFlight = null
  })
  return uploadInFlight
}

async function doUploadToNova(opts?: { trigger?: SyncTrigger }): Promise<NovaUploadResult> {
  const rt = getNovaRuntime()
  if (!rt.token || !rt.bindingId) {
    const result = { ok: false as const, status: 'unbound' as const, message: '尚未绑定对账平台' }
    broadcastNova(result)
    return result
  }
  const db = getDb()
  const customerSince = entitySince(rt, 'uploadWatermarkCustomer')
  const outboundSince = entitySince(rt, 'uploadWatermarkOutbound')
  const lineSince = entitySince(rt, 'uploadWatermarkLine')
  const startedAt = formatLocalTime()
  const startedMs = Date.now()

  const customers = db
    .prepare(
      `SELECT fid, fnumber, fname, address, disabled, sync_at
       FROM customer WHERE sync_at IS NOT NULL AND sync_at > ? ORDER BY sync_at, fid`
    )
    .all(customerSince) as CustomerRow[]
  const outbounds = db
    .prepare(
      `SELECT fid, fbillno, fdate, customer_fid, customer_name, status, cancelled, amount_cents, modify_time, sync_at
       FROM outbound WHERE sync_at IS NOT NULL AND sync_at > ? ORDER BY sync_at, fid`
    )
    .all(outboundSince) as OutboundRow[]
  const lines = db
    .prepare(
      `SELECT id, outbound_fid, entry_id, material_number, material_name, spec, unit,
              qty_milli, price_cents, amount_cents, src_order_no, customer_po, remark, sync_at
       FROM outbound_line WHERE sync_at IS NOT NULL AND sync_at > ?
       ORDER BY sync_at, outbound_fid, id`
    )
    .all(lineSince) as LineRow[]

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
    await retryTransient(() =>
      startBatch(rt.baseUrl, rt.token, {
        batchId,
        entity: 'ALL',
        mode: 'INCREMENTAL',
        clientVersion: app.getVersion()
      })
    )

    let ackedCustomers = 0
    for (const chunk of chunkRows(customers)) {
      const r = await uploadWithRetry(
        chunk,
        (rows) =>
          uploadCustomers(rt.baseUrl, rt.token, {
            batchId,
            rows: rows.map((row) => ({
              kdFid: row.fid,
              number: row.fnumber || '',
              name: row.fname || '',
              address: row.address || '',
              disabled: row.disabled ? 1 : 0
            }))
          }),
        {
          onAck: (acked) => {
            ackedCustomers += acked.length
            received += acked.length
            upserted += acked.length
            persistEntityWatermark('customer', customers, ackedCustomers)
          }
        }
      )
      skipped += r.skipped
    }

    let ackedOutbounds = 0
    for (const chunk of chunkRows(outbounds)) {
      const r = await uploadWithRetry(
        chunk,
        (rows) =>
          uploadOutbounds(rt.baseUrl, rt.token, {
            batchId,
            rows: rows.map((row) => ({
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
          }),
        {
          onAck: (acked) => {
            ackedOutbounds += acked.length
            received += acked.length
            upserted += acked.length
            persistEntityWatermark('outbound', outbounds, ackedOutbounds)
          }
        }
      )
      skipped += r.skipped
    }

    // 分录内码：有金蝶 entry_id 用原值；否则用本地 id 生成稳定键。
    // 不能再用「当前待上报数组下标」——半块失败后下次只剩后缀，下标会从 0 重来并覆盖已落库行。
    let ackedLines = 0
    for (const chunk of chunkRows(lines)) {
      const r = await uploadWithRetry(
        chunk,
        (rows) =>
          uploadLines(rt.baseUrl, rt.token, {
            batchId,
            rows: rows.map((row) => ({
              outboundFid: row.outbound_fid,
              entryId: resolveLocalLineEntryId(row),
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
          }),
        {
          onAck: (acked) => {
            ackedLines += acked.length
            received += acked.length
            upserted += acked.length
            persistEntityWatermark('line', lines, ackedLines)
          }
        }
      )
      skipped += r.skipped
    }

    const finishedAt = formatLocalTime()
    await retryTransient(() =>
      finishBatch(rt.baseUrl, rt.token, batchId, {
        status: 'SUCCESS',
        rowCount: received,
        upsertCount: upserted,
        skippedCount: skipped,
        // M1 水位语义：客户端完成本次上行的本地时间（服务端只用于展示与单调推进）；
        // 接入金蝶 FModifyDate 后改为真正的金蝶修改时间
        // 本地 sync_at 是 Date.toISOString()（带 Z）。后端 LocalDateTime 默认
        // 不吃 Instant；1.2.1 原样上报后 finish SUCCESS 被包装成「JSON 不合法」。
        watermarkTo: watermark ? toLocalTime(watermark) : undefined,
        startedAt,
        finishedAt,
        costMs: Date.now() - startedMs,
        message: `${uploadTriggerHint(opts?.trigger)}客户 ${customers.length}、出库 ${outbounds.length}、分录 ${lines.length}`
      })
    )

    const message = `${uploadTriggerHint(opts?.trigger)}已上报 客户 ${customers.length} / 出库 ${outbounds.length} / 分录 ${lines.length}`
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
    const message = `${uploadTriggerHint(opts?.trigger)}${e instanceof Error ? e.message : String(e)}`
    // 失败也要回报批次，服务端才能看到失败现场；回报本身失败不影响主流程
    try {
      await retryTransient(() =>
        finishBatch(rt.baseUrl, rt.token, batchId, {
          status: 'FAILED',
          rowCount: received,
          upsertCount: upserted,
          skippedCount: skipped,
          startedAt,
          finishedAt: formatLocalTime(),
          costMs: Date.now() - startedMs,
          message
        })
      )
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

function persistEntityWatermark(
  entity: 'customer' | 'outbound' | 'line',
  rows: Array<{ sync_at: string | null }>,
  ackedCount: number
) {
  const wm = safeWatermarkAfterPrefix(
    rows.map((r) => r.sync_at),
    ackedCount
  )
  if (!wm) return
  if (entity === 'customer') saveNovaUploadProgress({ watermarkCustomer: wm })
  else if (entity === 'outbound') saveNovaUploadProgress({ watermarkOutbound: wm })
  else saveNovaUploadProgress({ watermarkLine: wm })
}

/** 缺金蝶分录内码时用本地 id 生成稳定键，半块重跑不得改号 */
function resolveLocalLineEntryId(row: LineRow): number {
  if (row.entry_id && row.entry_id > 0) return row.entry_id
  return row.outbound_fid * 1_000_000 + (row.id || 0)
}

function uploadTriggerHint(trigger?: SyncTrigger): string {
  if (trigger === 'auto') return '定时同步 · '
  if (trigger === 'command') return '远程指令 · '
  return ''
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
