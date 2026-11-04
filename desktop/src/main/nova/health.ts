/**
 * 对账平台健康状态（同步可视化的一部分）。
 *
 * <p>三个信息源合成一张"服务是否正常"的图：
 * <ul>
 *     <li>主动探测（nova:probe）：点「立即检测」时真实请求一次 /kd/sync/tasks 并计时；</li>
 *     <li>指令轮询心跳：每 15 秒的轮询天然是持续健康信号，网络断了立刻能在页面上看到；</li>
 *     <li>绑定状态：未绑定时一切无从谈起，页面直接引导去绑定。</li>
 * </ul>
 *
 * <p>不新增后端接口：探测复用 /kd/sync/tasks（采集器令牌本来就能访问）。
 */
import { getNovaRuntime, getNovaState } from '../configStore'
import { getAutoSyncStatus } from '../tray'
import { fetchTasks } from './client'
import type { NovaHealth, NovaHeartbeat, NovaProbeResult } from '../../shared/types'

function nowIso() {
  return new Date().toISOString()
}

let heartbeat: NovaHeartbeat = {
  active: false,
  pollIntervalSec: 15
}

let lastProbe: NovaProbeResult | undefined

/** 指令轮询成功（commands.ts 每 15 秒调用一次） */
export function markHeartbeatOk() {
  heartbeat = { ...heartbeat, lastOkAt: nowIso(), lastFailMessage: undefined }
}

/** 指令轮询失败：网络断 / 服务端重启 / 令牌失效 */
export function markHeartbeatFail(message: string) {
  heartbeat = { ...heartbeat, lastFailAt: nowIso(), lastFailMessage: message }
}

/** 轮询启停（绑定后启动、解绑 / 退出时停止） */
export function setHeartbeatActive(active: boolean) {
  heartbeat = { ...heartbeat, active }
}

export function getHeartbeat(): NovaHeartbeat {
  return heartbeat
}

/**
 * 主动探测一次对账平台：真实请求 /kd/sync/tasks 并计时。
 * 结果缓存在 lastProbe，健康卡片显示最近一次探测。
 */
export async function probeNova(): Promise<NovaProbeResult> {
  const state = getNovaState()
  if (!state.bound) {
    lastProbe = {
      ok: false,
      status: 'unbound',
      message: '尚未绑定对账平台，请先在「连接」页绑定',
      checkedAt: nowIso()
    }
    return lastProbe
  }

  const rt = getNovaRuntime()
  const startMs = Date.now()
  try {
    const tasks = await fetchTasks(rt.baseUrl, rt.token ?? '')
    lastProbe = {
      ok: true,
      status: 'ok',
      message: `服务正常，下发 ${tasks.length} 个同步任务`,
      latencyMs: Date.now() - startMs,
      checkedAt: nowIso()
    }
  } catch (e) {
    lastProbe = {
      ok: false,
      status: 'error',
      message: e instanceof Error ? e.message : String(e),
      latencyMs: Date.now() - startMs,
      checkedAt: nowIso()
    }
  }
  return lastProbe
}

/** 综合健康状态（nova:health）：绑定 + 心跳 + 最近探测 + 定时同步 */
export function getNovaHealth(): NovaHealth {
  const state = getNovaState()
  return {
    state,
    // 未绑定时轮询循环虽在空转，但对用户来说"通道"就是没开
    heartbeat: { ...heartbeat, active: heartbeat.active && state.bound },
    lastProbe,
    autoSync: getAutoSyncStatus()
  }
}
