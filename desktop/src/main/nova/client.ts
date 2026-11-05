/**
 * nova 对账平台 HTTP 客户端（采集器上行）。
 *
 * <p>只做"发请求 + 解包 R<T>"，不碰本地配置与数据库。
 * 令牌放在请求头 {@code X-Kd-Token}，与后台 JWT 完全分开：
 * 采集器令牌只能访问 /kd/sync/**，拿不到任何后台管理能力。
 */

/** 后端统一响应包装 */
interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

/** 请求超时：金蝶侧可能慢，但 30s 足够；超时后客户端可安全重试（服务端幂等） */
const TIMEOUT_MS = 30_000

/**
 * 单次上行行数。
 *
 * <p>后端 @Size(max = 2000) 仍是硬上限。现场 500 行一批约 15–16s，
 * EdgeOne 源站读超时回 HTML 524，客户端再报「返回非 JSON」。
 * 100 行把单请求压到约原先 1/5，整批改走更多幂等 POST。
 */
export const UPLOAD_CHUNK = 100

/** 按块切分上报行；空数组得到空结果，末块允许不足一块。 */
export function chunkRows<T>(rows: T[], size = UPLOAD_CHUNK): T[][] {
  const result: T[][] = []
  for (let i = 0; i < rows.length; i += size) result.push(rows.slice(i, i + size))
  return result
}

export interface NovaCustomerRow {
  kdFid: number
  number: string
  name: string
  address: string
  disabled: number
}

export interface NovaOutboundRow {
  fid: number
  billNo: string
  bizDate?: string
  customerFid?: number
  customerName: string
  docStatus: string
  cancelStatus: string
  amountCents: number
  remark?: string
  modifyTime?: string
}

export interface NovaLineRow {
  outboundFid: number
  entryId?: number | null
  materialNumber: string
  materialName: string
  spec?: string
  unit: string
  qtyMilli: number
  priceCents: number
  amountCents: number
  srcOrderNo: string
  customerPo: string
  remark: string
}

export interface NovaUploadResult {
  received: number
  upserted: number
  skipped: number
  watermark?: string
}

/**
 * 本地时间格式化为 `yyyy-MM-dd HH:mm:ss`。
 *
 * <p>后端用 Jackson 的 LocalDateTime，默认不接受 `toISOString()` 的 `Z` 后缀，
 * 所以这里必须手工格式化成本地时间字符串。
 */
export function formatLocalTime(date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ` +
    `${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
  )
}

async function request<T>(
  baseUrl: string,
  path: string,
  init: { method: 'GET' | 'POST'; token?: string; body?: unknown }
): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (init.token) headers['X-Kd-Token'] = init.token

  let res: Response
  try {
    res = await fetch(`${baseUrl.replace(/\/+$/, '')}${path}`, {
      method: init.method,
      headers,
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      signal: controller.signal
    })
  } catch (e) {
    if (e instanceof Error && e.name === 'AbortError') {
      throw new Error(`请求超时（${TIMEOUT_MS / 1000}s）：${path}`)
    }
    throw new Error(`无法连接对账平台：${e instanceof Error ? e.message : String(e)}`)
  } finally {
    clearTimeout(timer)
  }

  if (res.status === 401) {
    throw new Error('采集器令牌已失效，请在「连接设置」里重新绑定')
  }
  const text = await res.text()
  let json: ApiResult<T>
  try {
    json = JSON.parse(text) as ApiResult<T>
  } catch {
    throw new Error(`对账平台返回非 JSON（HTTP ${res.status}）：${text.slice(0, 200)}`)
  }
  if (!res.ok || json.code !== 200) {
    throw new Error(json.msg || `请求失败（HTTP ${res.status}）`)
  }
  return json.data
}

export interface NovaRedeemResult {
  token: string
  bindingId: string
  userId: string
  acctId: string
  acctName?: string
  watermarkCustomer?: string
  watermarkOutbound?: string
}

/** 用一次性绑定码换取机器令牌（无需登录态） */
export async function redeemBinding(
  baseUrl: string,
  payload: {
    code: string
    acctId: string
    acctName?: string
    kdUsername?: string
    kdServerUrl?: string
    machineName?: string
    clientVersion?: string
  }
): Promise<NovaRedeemResult> {
  return request<NovaRedeemResult>(baseUrl, '/kd/binding/redeem', { method: 'POST', body: payload })
}

/** 开启批次（同批次号重复调用幂等） */
export async function startBatch(
  baseUrl: string,
  token: string,
  body: { batchId: string; entity?: string; mode?: string; clientVersion?: string }
): Promise<void> {
  await request<void>(baseUrl, '/kd/sync/batch', { method: 'POST', token, body })
}

/** 上报客户 */
export async function uploadCustomers(
  baseUrl: string,
  token: string,
  body: { batchId: string; rows: NovaCustomerRow[]; watermarkTo?: string }
): Promise<NovaUploadResult> {
  return request<NovaUploadResult>(baseUrl, '/kd/sync/customer', { method: 'POST', token, body })
}

/** 上报出库单头 */
export async function uploadOutbounds(
  baseUrl: string,
  token: string,
  body: { batchId: string; rows: NovaOutboundRow[]; watermarkTo?: string }
): Promise<NovaUploadResult> {
  return request<NovaUploadResult>(baseUrl, '/kd/sync/outbound', { method: 'POST', token, body })
}

/** 上报出库分录 */
export async function uploadLines(
  baseUrl: string,
  token: string,
  body: { batchId: string; rows: NovaLineRow[]; watermarkTo?: string }
): Promise<NovaUploadResult> {
  return request<NovaUploadResult>(baseUrl, '/kd/sync/outbound-line', { method: 'POST', token, body })
}

/** 服务端下发的任务清单（含该账套字段集） */
export interface NovaClientTask {
  entity: string
  formId: string
  endpoint: string
  fieldKeys: string
  watermarkField?: string
  watermark?: string
  mappings?: Record<string, string>
}

/** 服务端下发的指令 */
export interface NovaCommand {
  id: string
  bindingId?: string
  command: string
  payload?: string
  status: string
}

/** 拉取任务清单（服务端配置优先，本地兜底） */
export async function fetchTasks(baseUrl: string, token: string): Promise<NovaClientTask[]> {
  return request<NovaClientTask[]>(baseUrl, '/kd/sync/tasks', { method: 'GET', token })
}

/**
 * 取一条待执行指令；没有指令时返回 null。
 *
 * <p>请求本身就是心跳，服务端据此刷新 last_seen_at。
 */
export async function fetchCommand(baseUrl: string, token: string): Promise<NovaCommand | null> {
  return request<NovaCommand | null>(baseUrl, '/kd/sync/commands', { method: 'GET', token })
}

/** 指令回执 */
export async function ackCommand(
  baseUrl: string,
  token: string,
  id: string,
  ok: boolean,
  message: string
): Promise<void> {
  await request<void>(baseUrl, `/kd/sync/commands/${encodeURIComponent(id)}/ack`, {
    method: 'POST',
    token,
    body: { ok, message }
  })
}

/**
 * 字段自检结果回传：把自检拼出的分录字段集 / PO 字段写到本绑定的字段映射。
 *
 * <p>运维在网页「金蝶同步 → 同步配置」确认后下发 config.reload 生效，
 * 砍掉"业务员复制报告给运维、运维手抄配置"的一次往返。
 */
export async function uploadMapping(
  baseUrl: string,
  token: string,
  body: { lineFieldKeys?: string; customerPoField?: string; note?: string }
): Promise<void> {
  await request<void>(baseUrl, '/kd/sync/mapping', { method: 'POST', token, body })
}

/** 批次结束回报 */
export async function finishBatch(
  baseUrl: string,
  token: string,
  batchId: string,
  body: {
    status: 'SUCCESS' | 'FAILED'
    rowCount?: number
    upsertCount?: number
    skippedCount?: number
    watermarkTo?: string
    startedAt?: string
    finishedAt?: string
    costMs?: number
    message?: string
  }
): Promise<void> {
  await request<void>(baseUrl, `/kd/sync/batch/${encodeURIComponent(batchId)}/finish`, {
    method: 'POST',
    token,
    body
  })
}
