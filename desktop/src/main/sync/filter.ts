/**
 * 双水位同步策略（方案 §3.1 目标形态）。
 *
 * <p>此前增量按 `FDate` 过滤，**改过的历史单抓不到**（改单号/改数量后网页数字
 * 与金蝶不一致）；全量兜底又慢且依赖人触发。本模块实现双水位：
 * <ul>
 *     <li>主水位 = 金蝶修改时间 `FModifyDate`，从上次水位 − 15 分钟缓冲开始拉；</li>
 *     <li>每月按 `FDate` 回看 30 天（月度兜底，防止漏网的边界单）；</li>
 *     <li>用户显式填日期重拉 / 首次同步 → 退回 FDate 模式，行为与旧版一致。</li>
 * </ul>
 *
 * <p>纯函数、无 Electron / DB 依赖：主进程使用，也便于独立验证。
 */

/** 同步过滤决策结果 */
export interface SyncFilterDecision {
  /** fdate = 显式日期/旧模式；fmodify = 双水位增量；full = 全量 */
  mode: 'fdate' | 'fmodify' | 'full'
  /** 金蝶 FilterString；空串 = 不过滤（全量） */
  filter: string
  /** 本轮使用的金蝶修改水位（fmodify 模式，已含 15 分钟缓冲的原始值） */
  watermark?: string
  /** 月度回看的 FDate 起点（fmodify 模式且本轮带回看） */
  sweepFrom?: string
}

/** 决策输入（全部可测） */
export interface SyncFilterInput {
  /** 用户显式填写的重拉日期（yyyy-MM-dd）：非空时永远走 FDate 模式 */
  explicitSinceDate?: string
  /** 本账套是否实测支持 FModifyDate（探测结果缓存） */
  supportsModify: boolean
  /** 上次同步推进的金蝶修改水位（yyyy-MM-dd HH:mm:ss） */
  watermark?: string
  /** 上次月度回看时间（ISO 字符串） */
  lastSweepAt?: string
  /** 当前时间（默认 new Date()，测试可注入） */
  now?: Date
}

/** 水位缓冲：金蝶时间戳粒度与并发修改的容忍窗口 */
export const WATERMARK_BUFFER_MINUTES = 15
/** 月度回看：距上次回看超过该天数时，本轮附带按 FDate 回看 */
export const SWEEP_INTERVAL_DAYS = 30
/** 月度回看窗口：往前看 30 天的业务日期 */
export const SWEEP_WINDOW_DAYS = 30

/**
 * 决定本轮同步的过滤条件。
 *
 * @param input 决策输入
 * @returns 决策结果
 */
export function decideSyncFilter(input: SyncFilterInput): SyncFilterDecision {
  const explicit = (input.explicitSinceDate || '').trim()
  if (explicit) {
    return { mode: 'fdate', filter: `FDate>='${explicit}'` }
  }
  const watermark = (input.watermark || '').trim()
  if (!input.supportsModify || !watermark) {
    // 账套不支持 FModifyDate，或还没有水位（首次同步）→ 不过滤全量拉取，
    // 与旧行为一致；水位会在本轮成功后建立
    return { mode: 'full', filter: '' }
  }
  const now = input.now || new Date()
  const buffered = minusMinutes(watermark, WATERMARK_BUFFER_MINUTES)
  let filter = `FModifyDate>='${buffered}'`
  let sweepFrom: string | undefined
  if (shouldSweep(input.lastSweepAt, now)) {
    sweepFrom = toKdDate(addDays(now, -SWEEP_WINDOW_DAYS))
    // OR 兜底：月度回看按业务日期扫最近 30 天，抓水位边界漏掉的单
    filter = `(${filter} OR FDate>='${sweepFrom}')`
  }
  return { mode: 'fmodify', filter, watermark, sweepFrom }
}

/** 距上次月度回看是否超过间隔 */
function shouldSweep(lastSweepAt: string | undefined, now: Date): boolean {
  if (!lastSweepAt) {
    return true
  }
  const last = new Date(lastSweepAt).getTime()
  if (!Number.isFinite(last)) {
    return true
  }
  return now.getTime() - last > SWEEP_INTERVAL_DAYS * 86_400_000
}

/**
 * 金蝶返回的时间规范化为 `yyyy-MM-dd HH:mm:ss`。
 *
 * <p>金蝶 ExecuteBillQuery 的时间形如 `2026-11-03T10:00:00`、`2026-11-03 10:00:00`
 * 或带毫秒/时区后缀；截断到秒并统一分隔符后，字符串比较与 FilterString 拼接都安全。
 * 解析失败返回空串（调用方跳过水位推进，等下一轮修正）。
 *
 * @param value 金蝶时间原始值
 * @returns 规范化时间；无法解析返回空串
 */
export function normalizeKdTime(value: unknown): string {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  if (!text) return ''
  const sliced = text.slice(0, 19).replace('T', ' ')
  return /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(sliced) ? sliced : ''
}

/** 取两值中较大的金蝶时间（同格式字符串可直接比较；空值让位） */
export function maxKdTime(a: string, b: string): string {
  if (!a) return b
  if (!b) return a
  return a >= b ? a : b
}

function minusMinutes(time: string, minutes: number): string {
  const parsed = new Date(time.replace(' ', 'T')).getTime()
  if (!Number.isFinite(parsed)) {
    return time
  }
  return formatKdTime(new Date(parsed - minutes * 60_000))
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getTime() + days * 86_400_000)
}

function formatKdTime(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ` +
    `${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
  )
}

function toKdDate(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`
}
