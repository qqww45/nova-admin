/**
 * 墙钟定时同步调度。
 *
 * <p>旧实现用 {@code setInterval(2h)}：只计进程清醒时间。Windows 睡眠会冻结
 * libuv 计时，重启会把倒计时清零，首次还要空等满 2 小时。业务员白天间歇开机、
 * 中午合盖睡眠时永远攒不满 2 小时清醒时间，自动同步看起来像「从没跑过」，
 * 管理端同步批次就只剩他们手点的那些。
 *
 * <p>本模块按墙上时钟判断是否到期：短周期 tick（默认 30 秒）看
 * {@code now >= dueAt}。睡眠醒来第一拍、进程重启后若距上次自动同步已超过间隔，
 * 都会立刻补跑；从未跑过则等一段启动宽限（等金蝶/网络就绪），不必再空等 2 小时。
 */

/** 定时同步间隔：业务员白天开机，2 小时足够近实时又少碰金蝶许可 */
export const AUTO_SYNC_INTERVAL_MS = 2 * 3_600_000
/** 调度心跳：短到足以在睡眠醒来后几十秒内补跑，又不会空转打金蝶 */
export const AUTO_SYNC_TICK_MS = 30_000
/** 启动后首次自动同步的宽限（从未跑过时） */
export const AUTO_SYNC_STARTUP_GRACE_MS = 60_000

export interface AutoSyncDecisionInput {
  nowMs: number
  /** 本进程启动时间 */
  startedAtMs: number
  /** 上次自动同步的墙钟时间；从未跑过则为 undefined */
  lastAutoRunAtMs?: number
  intervalMs?: number
  startupGraceMs?: number
}

export interface AutoSyncDecision {
  due: boolean
  nextDueAtMs: number
}

/**
 * 纯函数：现在该不该跑一轮自动同步。
 *
 * @param input 当前墙钟、启动时间、上次自动同步
 */
export function decideAutoSync(input: AutoSyncDecisionInput): AutoSyncDecision {
  const intervalMs = input.intervalMs ?? AUTO_SYNC_INTERVAL_MS
  const graceMs = input.startupGraceMs ?? AUTO_SYNC_STARTUP_GRACE_MS
  const nextDueAtMs =
    input.lastAutoRunAtMs != null ? input.lastAutoRunAtMs + intervalMs : input.startedAtMs + graceMs
  return {
    due: input.nowMs >= nextDueAtMs,
    nextDueAtMs
  }
}

export interface AutoSyncSchedulerDeps {
  now?: () => number
  startedAtMs?: number
  tickMs?: number
  intervalMs?: number
  startupGraceMs?: number
  enabled?: boolean
  getLastAutoRunAtMs: () => number | undefined
  /** 绑定等门槛：到期但不可跑时不推进 lastRun，绑定后下一拍补跑 */
  canRun: () => boolean
  run: () => Promise<void>
  interval?: (fn: () => void, ms: number) => unknown
  clear?: (id: unknown) => void
}

export interface AutoSyncSchedulerStatus {
  enabled: boolean
  intervalHours: number
  startupGraceSec: number
  nextRunAtMs: number
}

export interface AutoSyncScheduler {
  start: () => void
  stop: () => void
  snapshot: () => AutoSyncDecision
  status: () => AutoSyncSchedulerStatus
}

/**
 * 短周期 tick 的墙钟调度器。{@link start} 幂等；{@link stop} 只清计时器。
 */
export function createAutoSyncScheduler(deps: AutoSyncSchedulerDeps): AutoSyncScheduler {
  const now = deps.now ?? Date.now
  const startedAtMs = deps.startedAtMs ?? now()
  const tickMs = deps.tickMs ?? AUTO_SYNC_TICK_MS
  const intervalMs = deps.intervalMs ?? AUTO_SYNC_INTERVAL_MS
  const startupGraceMs = deps.startupGraceMs ?? AUTO_SYNC_STARTUP_GRACE_MS
  const enabled = deps.enabled !== false
  const setInt = deps.interval ?? (setInterval as AutoSyncSchedulerDeps['interval'])!
  const clearInt = deps.clear ?? (clearInterval as AutoSyncSchedulerDeps['clear'])!
  let timer: unknown
  let inFlight = false

  function snapshot(): AutoSyncDecision {
    return decideAutoSync({
      nowMs: now(),
      startedAtMs,
      lastAutoRunAtMs: deps.getLastAutoRunAtMs(),
      intervalMs,
      startupGraceMs
    })
  }

  async function tick(): Promise<void> {
    if (inFlight) return
    if (!snapshot().due) return
    if (!deps.canRun()) return
    inFlight = true
    try {
      await deps.run()
    } finally {
      inFlight = false
    }
  }

  return {
    start() {
      if (timer !== undefined) return
      void tick()
      timer = setInt(() => {
        void tick()
      }, tickMs)
    },
    stop() {
      if (timer !== undefined) {
        clearInt(timer)
        timer = undefined
      }
    },
    snapshot,
    status() {
      const { nextDueAtMs } = snapshot()
      return {
        enabled,
        intervalHours: intervalMs / 3_600_000,
        startupGraceSec: startupGraceMs / 1000,
        nextRunAtMs: nextDueAtMs
      }
    }
  }
}
