import { getNovaRuntime } from '../configStore'
import { fetchTasks, type NovaClientTask } from './client'

/**
 * 服务端任务清单缓存。
 *
 * <p>M3 的落地方式：**服务端字段集只是"首选候选"**，失败仍回落到本机映射与内置候选。
 * 理由：采集器的字段自检是逐字段验证出来的，现场账套千差万别；把服务端配置当唯一
 * 来源，一旦运维填错就会让所有采集器同步失败。候选降级链才是现场可靠性的保障。
 *
 * <p>缓存 10 分钟，绑定成功、收到 config.reload 指令时强制刷新。
 */

const CACHE_TTL_MS = 10 * 60 * 1000

let cache: { at: number; tasks: NovaClientTask[] } | null = null

/**
 * 刷新任务清单（失败保留旧缓存）。
 *
 * @param force 忽略 TTL
 * @returns 任务列表
 */
export async function refreshTasks(force = false): Promise<NovaClientTask[]> {
  const rt = getNovaRuntime()
  if (!rt.token) return []
  if (!force && cache && Date.now() - cache.at < CACHE_TTL_MS) return cache.tasks
  try {
    const tasks = await fetchTasks(rt.baseUrl, rt.token)
    cache = { at: Date.now(), tasks: Array.isArray(tasks) ? tasks : [] }
  } catch {
    // 网络不通就用旧配置：宁可同步继续跑，也不要因为拉配置失败而卡住
  }
  return cache?.tasks ?? []
}

/**
 * 服务端给「出库分录」配置的字段集（没有则返回 undefined，走本地映射）。
 *
 * <p>只用于分录：客户 / 订单的解析是按位置取下标，服务端改顺序会导致错列，
 * 因此那两张表的字段集仍以本机为准。
 */
export function serverLineFieldKeys(): string | undefined {
  const task = cache?.tasks.find((t) => (t.entity || '').toUpperCase() === 'OUTBOUND_LINE')
  const keys = (task?.fieldKeys || '').trim()
  return keys || undefined
}

/** 清空缓存（重载配置 / 解绑时调用） */
export function clearTaskCache(): void {
  cache = null
}
