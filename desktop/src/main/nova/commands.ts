import { app } from 'electron'
import { clearNovaBinding, getNovaRuntime } from '../configStore'
import { runSync } from '../sync/runner'
import { checkForUpdatesSilent } from '../updater'
import { ackCommand, fetchCommand, type NovaCommand } from './client'
import { markHeartbeatFail, markHeartbeatOk, setHeartbeatActive } from './health'
import { clearTaskCache, refreshTasks } from './tasks'
import { uploadToNova } from './uploader'

/**
 * 指令轮询循环。
 *
 * <p>每 15 秒向对账平台取一条指令；一次只处理一条，处理完再回执。
 * 选择轮询而不是 WebSocket：现场多在反向代理后面，长连接容易被掐断，
 * 而指令本身已经落库，"15 秒内必达"足够满足"点了按钮希望它现在同步"的诉求。
 *
 * <p>失败不抛出：网络问题下个周期重试即可，绝不能让主进程崩掉。
 */

const POLL_MS = 15_000

let timer: ReturnType<typeof setInterval> | undefined
let busy = false

/** 启动（重复调用幂等） */
export function startCommandLoop(): void {
  if (timer) return
  setHeartbeatActive(true)
  void tick()
  timer = setInterval(() => void tick(), POLL_MS)
}

/** 停止（解绑 / 退出时调用） */
export function stopCommandLoop(): void {
  if (timer) clearInterval(timer)
  timer = undefined
  setHeartbeatActive(false)
}

/** 一次轮询 */
async function tick(): Promise<void> {
  // 上一条还没处理完就跳过本轮，避免"同步中又收到 sync.now"把业务锁顶掉
  if (busy) return
  const rt = getNovaRuntime()
  if (!rt.token) return
  busy = true
  try {
    const command = await fetchCommand(rt.baseUrl, rt.token)
    // 请求本身即心跳：成功 / 失败都记录，同步页的「服务状态」据此展示
    markHeartbeatOk()
    if (!command) return
    await handle(rt.baseUrl, rt.token, command)
  } catch (e) {
    // 网络抖动 / 服务端重启：静默重试
    markHeartbeatFail(e instanceof Error ? e.message : String(e))
  } finally {
    busy = false
  }
}

/**
 * 执行一条指令并回执。
 *
 * @param baseUrl 对账平台地址
 * @param token 机器令牌
 * @param command 指令
 */
async function handle(baseUrl: string, token: string, command: NovaCommand): Promise<void> {
  let ok = true
  let message = ''
  let after: (() => void) | undefined

  try {
    switch (command.command) {
      case 'sync.now': {
        // payload='full' 才全量重拉（账套数据不对时的纠错手段）；
        // 普通「立即同步」走自动模式：有水位增量、无水位全量，
        // 不再每次点一下就把整个账套重拉一遍
        const full = (command.payload || '').trim().toLowerCase() === 'full'
        const results = await runSync({
          sinceDate: full ? '' : undefined,
          trigger: 'command'
        })
        const upload = await uploadToNova()
        message = `${results.map((r) => r.message).join('；')}；上行：${upload.message}`
        break
      }
      case 'config.reload': {
        clearTaskCache()
        const tasks = await refreshTasks(true)
        message = `已重载 ${tasks.length} 个任务配置`
        break
      }
      case 'token.revoke': {
        // 先回执再删令牌：令牌一删这次请求就发不出去了
        after = () => {
          clearNovaBinding()
          clearTaskCache()
          stopCommandLoop()
        }
        message = '本机令牌已吊销'
        break
      }
      case 'client.update': {
        if (app.isPackaged) {
          await checkForUpdatesSilent()
          message = '已触发升级检查'
        } else {
          message = '开发模式忽略升级指令'
        }
        break
      }
      default: {
        ok = false
        message = `未知指令：${command.command}`
      }
    }
  } catch (e) {
    ok = false
    message = e instanceof Error ? e.message : String(e)
  }

  try {
    await ackCommand(baseUrl, token, command.id, ok, message.slice(0, 380))
  } catch {
    // 回执发不出去也没关系：指令已置 SENT，页面能看到"未回执"
  }
  after?.()
}
