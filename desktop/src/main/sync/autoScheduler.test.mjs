import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  AUTO_SYNC_INTERVAL_MS,
  AUTO_SYNC_STARTUP_GRACE_MS,
  decideAutoSync,
  createAutoSyncScheduler
} from './autoScheduler.ts'

const HOUR = 3_600_000

test('从未自动同步时，启动宽限内不触发（避免开机立刻打金蝶）', () => {
  const startedAtMs = 1_000_000
  const d = decideAutoSync({
    nowMs: startedAtMs + AUTO_SYNC_STARTUP_GRACE_MS - 1,
    startedAtMs
  })
  assert.equal(d.due, false)
  assert.equal(d.nextDueAtMs, startedAtMs + AUTO_SYNC_STARTUP_GRACE_MS)
})

test('从未自动同步时，过了启动宽限应触发——不必再空等 2 小时', () => {
  const startedAtMs = 1_000_000
  const d = decideAutoSync({
    nowMs: startedAtMs + AUTO_SYNC_STARTUP_GRACE_MS,
    startedAtMs
  })
  assert.equal(d.due, true)
  assert.equal(d.nextDueAtMs, startedAtMs + AUTO_SYNC_STARTUP_GRACE_MS)
})

test('上次自动同步未满间隔不触发', () => {
  const startedAtMs = 1_000_000
  const lastAutoRunAtMs = startedAtMs + 10_000
  const d = decideAutoSync({
    nowMs: lastAutoRunAtMs + AUTO_SYNC_INTERVAL_MS - 1,
    startedAtMs,
    lastAutoRunAtMs
  })
  assert.equal(d.due, false)
  assert.equal(d.nextDueAtMs, lastAutoRunAtMs + AUTO_SYNC_INTERVAL_MS)
})

test('上次自动同步已超过间隔应立即补跑（重启 / 睡眠后墙钟已过）', () => {
  const startedAtMs = 10_000_000
  const lastAutoRunAtMs = startedAtMs - 3 * HOUR
  const d = decideAutoSync({
    nowMs: startedAtMs + 5_000,
    startedAtMs,
    lastAutoRunAtMs
  })
  assert.equal(d.due, true)
  assert.equal(d.nextDueAtMs, lastAutoRunAtMs + AUTO_SYNC_INTERVAL_MS)
})

test('旧 setInterval(2h) 语义在睡眠后会漏跑：墙钟过了 3 小时但进程只醒了 30 秒', () => {
  // 对照：若按「进程启动 + 2 小时」算，睡眠醒来 now=start+30s，旧实现会判未到期。
  // 墙钟调度必须用 lastAutoRunAt，3 小时前跑过 → 现在到期。
  const startedAtMs = 50_000_000
  const lastAutoRunAtMs = startedAtMs - 3 * HOUR
  const awakeOnlyMs = 30_000
  const byUptime = startedAtMs + awakeOnlyMs < startedAtMs + AUTO_SYNC_INTERVAL_MS
  assert.equal(byUptime, true, '对照：只看进程清醒时间会认为还没到')
  const d = decideAutoSync({
    nowMs: startedAtMs + awakeOnlyMs,
    startedAtMs,
    lastAutoRunAtMs
  })
  assert.equal(d.due, true)
})

test('调度器：宽限过后触发一次，执行中不重入', async () => {
  let now = 0
  const ticks = []
  let runs = 0
  let lastAuto
  let resolveRun
  const running = new Promise((resolve) => {
    resolveRun = resolve
  })

  const scheduler = createAutoSyncScheduler({
    now: () => now,
    startedAtMs: 0,
    tickMs: 1_000,
    intervalMs: 2 * HOUR,
    startupGraceMs: 60_000,
    getLastAutoRunAtMs: () => lastAuto,
    canRun: () => true,
    run: async () => {
      runs += 1
      lastAuto = now
      await running
    },
    interval: (fn, ms) => {
      ticks.push({ fn, ms })
      return 1
    },
    clear: () => {}
  })

  scheduler.start()
  assert.equal(ticks.length, 1)
  assert.equal(ticks[0].ms, 1_000)
  assert.equal(runs, 0, '启动瞬间未过宽限，不应跑')

  now = 59_000
  await ticks[0].fn()
  assert.equal(runs, 0)

  now = 60_000
  const first = ticks[0].fn()
  await Promise.resolve()
  assert.equal(runs, 1)

  now = 60_500
  await ticks[0].fn()
  assert.equal(runs, 1, '上一次还没结束，不能叠跑')

  resolveRun()
  await first
  scheduler.stop()
})

test('调度器：未绑定到期不跑，绑定后下一拍补跑', async () => {
  let now = 0
  let bound = false
  let runs = 0
  let lastAuto
  let tickFn = async () => {}

  const scheduler = createAutoSyncScheduler({
    now: () => now,
    startedAtMs: 0,
    tickMs: 1_000,
    intervalMs: 2 * HOUR,
    startupGraceMs: 60_000,
    getLastAutoRunAtMs: () => lastAuto,
    canRun: () => bound,
    run: async () => {
      runs += 1
      lastAuto = now
    },
    interval: (fn) => {
      tickFn = fn
      return 1
    },
    clear: () => {}
  })

  scheduler.start()
  now = 60_000
  await tickFn()
  assert.equal(runs, 0)
  assert.equal(scheduler.snapshot().due, true)

  bound = true
  await tickFn()
  assert.equal(runs, 1)
  scheduler.stop()
})

test('主进程启动会拉起墙钟调度，且不再用 2 小时 setInterval', () => {
  const tray = readFileSync(resolve(import.meta.dirname, '../tray.ts'), 'utf8')
  const index = readFileSync(resolve(import.meta.dirname, '../index.ts'), 'utf8')
  assert.match(index, /startAutoSync\(\)/)
  assert.match(tray, /createAutoSyncScheduler/)
  assert.match(tray, /startAutoSync\(\)/)
  assert.doesNotMatch(
    tray,
    /setInterval\(\(\) => void autoSync\('定时'\),\s*AUTO_SYNC_HOURS/
  )
})

test('status 给出下次预计时间，间隔按小时回传', () => {
  const startedAtMs = 0
  const scheduler = createAutoSyncScheduler({
    now: () => 10_000,
    startedAtMs,
    getLastAutoRunAtMs: () => undefined,
    canRun: () => false,
    run: async () => {},
    interval: () => 1,
    clear: () => {}
  })
  const status = scheduler.status()
  assert.equal(status.enabled, true)
  assert.equal(status.intervalHours, AUTO_SYNC_INTERVAL_MS / HOUR)
  assert.equal(status.startupGraceSec, AUTO_SYNC_STARTUP_GRACE_MS / 1000)
  assert.equal(status.nextRunAtMs, AUTO_SYNC_STARTUP_GRACE_MS)
  scheduler.stop()
})
