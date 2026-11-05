/**
 * 托盘常驻 + 定时同步（方案 §8「托盘常驻 + 开机自启 + 定时同步」）。
 *
 * <p>解决的现场问题：电脑重启后采集器不启动、业务员不点同步 → 数据断档数天
 * 无人发现（在线状态列只能让断档"可见"，托盘 + 定时让断档"不发生"）。
 *
 * <p>行为：
 * <ul>
 *     <li>点窗口关闭按钮 = 隐藏到托盘（退出只能走托盘菜单，防止误关）；</li>
 *     <li>托盘菜单：显示主窗口 / 立即同步并上报 / 开机自启开关（仅打包后）/ 退出；</li>
 *     <li>单击托盘图标 = 显示主窗口，右键 = 菜单；</li>
 *     <li>墙钟每 AUTO_SYNC_HOURS 小时自动同步 + 上行（有绑定才执行；同步冲突自动让位；
 *         启动约 1 分钟后会跑第一次，睡眠/重启后按上次自动同步补跑）；</li>
 *     <li>开机自启默认开启（业务员电脑重启后采集器自动拉起），仅打包后设置。</li>
 * </ul>
 *
 * <p>开发模式同样启用（便于验证托盘行为）；系统不支持托盘（部分 Linux）时
 * 创建失败，退回"关窗即退"，绝不把窗口藏进找不到的地方。
 */
import { app, BrowserWindow, Menu, Tray, nativeImage } from 'electron'
import { runSync } from './sync/runner'
import {
  AUTO_SYNC_INTERVAL_MS,
  AUTO_SYNC_STARTUP_GRACE_MS,
  createAutoSyncScheduler,
  type AutoSyncScheduler
} from './sync/autoScheduler'
import { uploadToNova } from './nova/uploader'
import { getNovaRuntime } from './configStore'
import { getDb } from './db'
import { getAppIconPath } from './paths'
import { existsSync } from 'fs'
import type { AutoSyncStatus } from '../shared/types'

/** 定时同步间隔（小时）：业务员白天开机，2 小时足够近实时又不动金蝶许可 */
export const AUTO_SYNC_HOURS = AUTO_SYNC_INTERVAL_MS / 3_600_000

let tray: Tray | null = null
let scheduler: AutoSyncScheduler | undefined
let autoSyncEnabled = true
let quitting = false

/** 定时同步运行轨迹（进程内存；重启后 lastRun 从 sync_run 里找回来） */
let lastAutoRunAt: string | undefined
let lastAutoRunOk: boolean | undefined
let lastAutoRunMessage: string | undefined

/** 退出流程中不再拦截窗口关闭（托盘菜单退出 / 安装更新时置 true） */
export function markQuitting() {
  quitting = true
}

/** 显示并聚焦主窗口（托盘菜单 / 单击图标共用） */
function showMainWindow() {
  const win = BrowserWindow.getAllWindows()[0]
  if (!win) return
  if (win.isMinimized()) win.restore()
  win.show()
  win.focus()
}

/**
 * 初始化托盘与窗口关闭行为。
 *
 * <p>先挂关窗拦截，再等图标（避免启动瞬间关窗直接退出）；托盘创建失败则卸掉拦截，
 * 退回"关窗即退"，绝不把窗口藏进找不到的地方。
 */
export async function initTray() {
  if (tray) return

  // 调度器与托盘解耦：托盘创建失败（部分 Linux）时窗口还在，自动同步仍应跑。
  // 旧实现把 startAutoSyncTimer 放在 new Tray 成功之后，创建失败就永远不会定时。
  startAutoSync()

  // 关窗 = 隐藏到托盘；首次隐藏提示一次，防止用户以为程序退出（后台还在同步）
  let hinted = false
  const attached: BrowserWindow[] = []
  const onCreated = (_e: Electron.Event, win: BrowserWindow) => attachHideOnClose(win)
  BrowserWindow.getAllWindows().forEach((win) => attachHideOnClose(win))
  app.on('browser-window-created', onCreated)

  function attachHideOnClose(win: BrowserWindow) {
    if (attached.includes(win)) return
    attached.push(win)
    win.on('close', (e) => {
      if (quitting) return
      e.preventDefault()
      win.hide()
      if (!hinted) {
        hinted = true
        tray?.displayBalloon?.({
          iconType: 'info',
          title: '衡账仍在后台运行',
          content: '采集器已最小化到托盘，会按设定自动同步。右键托盘图标可退出。'
        })
      }
    })
  }

  let image = nativeImage.createEmpty()
  const iconPng = getAppIconPath('icon.png')
  if (existsSync(iconPng)) {
    image = nativeImage.createFromPath(iconPng)
    if (!image.isEmpty() && image.getSize().width > 16) {
      image = image.resize({ width: 16, height: 16 })
    }
  }
  if (image.isEmpty()) {
    try {
      image = await app.getFileIcon(process.execPath, { size: 'small' })
    } catch {
      /* 系统不支持取 exe 图标时保持空图，托盘菜单仍可点 */
    }
  }

  try {
    tray = new Tray(image.isEmpty() ? nativeImage.createEmpty() : image)
  } catch {
    // 系统没有托盘支持：卸掉关窗拦截，保持默认"关窗即退"
    app.off('browser-window-created', onCreated)
    attached.forEach((win) => win.removeAllListeners('close'))
    tray = null
    return
  }

  rebuildTrayMenu()
  // 单击托盘图标 = 显示主窗口（右键才是菜单），符合 Windows 习惯
  tray.on('click', () => showMainWindow())
}

/** 托盘菜单（开机自启开关状态变化时重建） */
function rebuildTrayMenu() {
  if (!tray) return
  tray.setToolTip('衡账 · 金蝶采集器')
  const menu: Array<Electron.MenuItemConstructorOptions> = [
    { label: '显示主窗口', click: () => showMainWindow() },
    { type: 'separator' },
    { label: '立即同步并上报', click: () => void autoSync('手动') }
  ]
  if (app.isPackaged) {
    menu.push({
      label: `开机自启（当前：${app.getLoginItemSettings().openAtLogin ? '开' : '关'}）`,
      click: () => {
        const next = !app.getLoginItemSettings().openAtLogin
        app.setLoginItemSettings({ openAtLogin: next })
        rebuildTrayMenu()
      }
    })
  }
  menu.push({ type: 'separator' }, {
    label: '退出',
    click: () => {
      markQuitting()
      app.quit()
    }
  })
  tray.setContextMenu(Menu.buildFromTemplate(menu))
}

/**
 * 启动墙钟定时同步（幂等）。
 *
 * <p>不依赖托盘是否创建成功；未绑定则到期空转，绑定后下一拍补跑。
 */
export function startAutoSync() {
  if (!scheduler) {
    scheduler = createAutoSyncScheduler({
      getLastAutoRunAtMs: () => parseIsoMs(lastAutoRunAt) ?? lastAutoFromDbMs(),
      canRun: () => {
        if (!autoSyncEnabled || quitting) return false
        const rt = getNovaRuntime()
        return Boolean(rt.token && rt.bindingId)
      },
      run: () => autoSync('定时')
    })
  }
  scheduler.start()
}

/**
 * 执行一次同步 + 上行并气泡提示结果。
 *
 * @param trigger 触发来源（手动 / 定时），仅用于提示文案
 */
async function autoSync(trigger: '手动' | '定时') {
  const rt = getNovaRuntime()
  if (!rt.token || !rt.bindingId) {
    if (tray) tray.setToolTip('衡账 · 未绑定对账平台')
    return
  }
  // 「上次 / 下次自动同步」只跟定时周期走；托盘菜单点的不算
  if (trigger === '定时') {
    lastAutoRunAt = new Date().toISOString()
  }
  try {
    // 业务锁在同步进行中会直接抛错（"已有同步或其它任务进行中"）——让位即可
    const results = await runSync({ trigger: trigger === '定时' ? 'auto' : 'manual' })
    const upload = await uploadToNova({ trigger: trigger === '定时' ? 'auto' : 'manual' })
    if (trigger === '定时') {
      lastAutoRunAt = results[results.length - 1]?.finishedAt || lastAutoRunAt
      lastAutoRunOk = results.every((r) => r.status === 'success')
      lastAutoRunMessage = `${results.map((r) => r.message).join('；')}；上行：${upload.message}`
    }
    tray?.displayBalloon?.({
      iconType: 'info',
      title: `同步完成（${trigger}）`,
      content: `${results.map((r) => r.message).join('；')}；上行：${upload.message}`
    })
    if (trigger === '定时' && lastAutoRunAt) {
      tray?.setToolTip(`衡账 · 上次同步 ${formatClock(lastAutoRunAt)}`)
    }
  } catch (e) {
    // 同步失败不打扰业务员（托盘 tooltip 留痕），下个周期自动重试
    if (trigger === '定时') {
      lastAutoRunOk = false
      lastAutoRunMessage = e instanceof Error ? e.message : String(e)
    }
    tray?.displayBalloon?.({
      iconType: 'warning',
      title: `同步未完成（${trigger}）`,
      content: e instanceof Error ? e.message : String(e)
    })
    tray?.setToolTip(`衡账 · 上次同步失败 ${formatClock(new Date().toISOString())}`)
  }
}

/** 托盘定时同步状态（同步页「定时同步」卡片展示用） */
export function getAutoSyncStatus(): AutoSyncStatus {
  let lastRunAt = lastAutoRunAt
  let lastRunOk = lastAutoRunOk
  let lastRunMessage = lastAutoRunMessage

  if (!lastRunAt) {
    const row = lastAutoRowFromDb()
    if (row) {
      lastRunAt = row.startedAt
      lastRunOk = row.status === 'success'
      lastRunMessage = row.message
    }
  }

  const sched = scheduler?.status()
  return {
    enabled: autoSyncEnabled,
    intervalHours: sched?.intervalHours ?? AUTO_SYNC_HOURS,
    startupGraceSec: sched?.startupGraceSec ?? AUTO_SYNC_STARTUP_GRACE_MS / 1000,
    lastRunAt,
    lastRunOk,
    lastRunMessage,
    nextRunAt: sched ? new Date(sched.nextRunAtMs).toISOString() : undefined
  }
}

/** 停止定时器（退出时调用） */
export function stopAutoSync() {
  scheduler?.stop()
  scheduler = undefined
}

/** 是否启用定时同步（预留：后续可加设置项） */
export function isAutoSyncEnabled(): boolean {
  return autoSyncEnabled
}

function parseIsoMs(iso?: string): number | undefined {
  if (!iso) return undefined
  const t = Date.parse(iso)
  return Number.isNaN(t) ? undefined : t
}

function lastAutoRowFromDb(): { startedAt: string; status: string; message: string } | undefined {
  try {
    return getDb()
      .prepare(
        `SELECT started_at AS startedAt, status, message FROM sync_run
         WHERE source = 'auto' ORDER BY id DESC LIMIT 1`
      )
      .get() as { startedAt: string; status: string; message: string } | undefined
  } catch {
    return undefined
  }
}

function lastAutoFromDbMs(): number | undefined {
  return parseIsoMs(lastAutoRowFromDb()?.startedAt)
}

/** ISO 时间 → HH:mm（托盘 tooltip 用） */
function formatClock(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}
