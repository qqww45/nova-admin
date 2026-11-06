/**
 * 上行瞬时失败处理：524 / 超时后有限重试，并按已确认前缀安全推进水位。
 *
 * <p>EdgeOne 源站超时回 HTML 524，整批失败若回拨水位，下次会把已 upsert 的
 * 两三万行再传一遍。服务端按金蝶内码幂等，已确认的块可以留下进度。
 */

export const TRANSIENT_RETRY_ATTEMPTS = 3

const TRANSIENT_RE = /HTTP 52[0-4]|返回非 JSON|请求超时|无法连接对账平台/

export interface UploadCounts {
  received: number
  upserted: number
  skipped: number
}

export function isTransientUploadError(err: unknown): boolean {
  const msg = err instanceof Error ? err.message : String(err)
  return TRANSIENT_RE.test(msg)
}

/** 取最大 ISO/本地时间字符串（字典序可比较） */
export function maxSyncAt(values: Array<string | null | undefined>): string | undefined {
  let max: string | undefined
  for (const v of values) {
    if (v && (!max || v > max)) max = v
  }
  return max
}

/**
 * 已确认前缀的安全水位：只有「该时刻的行全部传完」才推进到该时刻。
 * 查询用 {@code sync_at > watermark}，同秒未传完的行不能被跳过。
 */
export function safeWatermarkAfterPrefix(
  syncAts: Array<string | null | undefined>,
  uploadedCount: number
): string | undefined {
  if (uploadedCount <= 0) return undefined
  if (uploadedCount >= syncAts.length) return maxSyncAt(syncAts)
  const next = syncAts[uploadedCount] || ''
  let watermark: string | undefined
  for (let i = 0; i < uploadedCount; i++) {
    const t = syncAts[i]
    if (t && t < next && (!watermark || t > watermark)) watermark = t
  }
  return watermark
}

/** 水位只进不退 */
export function mergeWatermark(
  current?: string,
  next?: string
): string | undefined {
  if (!next) return current
  if (!current) return next
  return next > current ? next : current
}

export function combineUploadResults(a: UploadCounts, b: UploadCounts): UploadCounts {
  return {
    received: a.received + b.received,
    upserted: a.upserted + b.upserted,
    skipped: a.skipped + b.skipped
  }
}

export function backoffMs(attempt: number): number {
  return 1000 * 2 ** (attempt - 1)
}

async function defaultSleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms))
}

export async function retryTransient<T>(
  fn: () => Promise<T>,
  opts?: {
    attempts?: number
    sleep?: (ms: number) => Promise<void>
    backoffMs?: (attempt: number) => number
  }
): Promise<T> {
  const attempts = opts?.attempts ?? TRANSIENT_RETRY_ATTEMPTS
  const sleep = opts?.sleep ?? defaultSleep
  const delayOf = opts?.backoffMs ?? backoffMs
  let last: unknown
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await fn()
    } catch (e) {
      last = e
      if (!isTransientUploadError(e) || attempt >= attempts) throw e
      await sleep(delayOf(attempt))
    }
  }
  throw last
}

/**
 * 先原块有限重试；仍瞬时失败且多于 1 行则对半切分再传。
 * 单行耗尽次数后放弃，避免无限循环。
 */
export async function uploadWithRetry<T>(
  rows: T[],
  upload: (chunk: T[]) => Promise<UploadCounts>,
  opts?: {
    attempts?: number
    sleep?: (ms: number) => Promise<void>
    backoffMs?: (attempt: number) => number
    onAck?: (acked: T[]) => void
  }
): Promise<UploadCounts> {
  if (rows.length === 0) return { received: 0, upserted: 0, skipped: 0 }
  try {
    const result = await retryTransient(() => upload(rows), opts)
    opts?.onAck?.(rows)
    return result
  } catch (e) {
    if (!isTransientUploadError(e) || rows.length <= 1) throw e
    const mid = Math.ceil(rows.length / 2)
    const left = await uploadWithRetry(rows.slice(0, mid), upload, opts)
    const right = await uploadWithRetry(rows.slice(mid), upload, opts)
    return combineUploadResults(left, right)
  }
}
