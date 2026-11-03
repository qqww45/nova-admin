/**
 * 托盘常驻 + 定时同步（方案 §8「托盘常驻 + 开机自启 + 定时同步」）。
 *
 * <p>解决的现场问题：电脑重启后采集器不启动、业务员不点同步 → 数据断档数天
 * 无人发现（在线状态列只能让断档"可见"，托盘 + 定时让断档"不发生"）。
 *
 * <p>行为：
 * <ul>
 *     <li>点窗口关闭按钮 = 隐藏到托盘（退出只能走托盘菜单，防止误关）；</li>
 *     <li>托盘菜单：显示主窗口 / 立即同步 / 开机自启开关 / 退出；</li>
 *     <li>每 AUTO_SYNC_HOURS 小时自动同步 + 上行（有绑定才执行；同步冲突自动让位）；</li>
 *     <li>开机自启默认开启（业务员电脑重启后采集器自动拉起）。</li>
 * </ul>
 *
 * <p>仅打包后生效；开发模式保持"关窗即退"，不干扰开发。
 */
import { app, BrowserWindow, Menu, Tray, nativeImage } from 'electron'
import { runSync } from './sync/runner'
import { uploadToNova } from './nova/uploader'
import { getNovaRuntime } from './configStore'

/** 定时同步间隔（小时）：业务员白天开机，2 小时足够近实时又不动金蝶许可 */
export const AUTO_SYNC_HOURS = 2

let tray: Tray | null = null
let autoTimer: ReturnType<typeof setInterval> | undefined
let autoSyncEnabled = true
let quitting = false

/** 退出流程中不再拦截窗口关闭（托盘菜单退出 / 安装更新时置 true） */
export function markQuitting() {
  quitting = true
}

/**
 * 初始化托盘与窗口关闭行为。
 *
 * @param getWindow 取主窗口（可能未创建）
 */
export async function initTray(getWindow: () => BrowserWindow | null) {
  if (!app.isPackaged) {
    // 开发模式：关窗即退，托盘与定时都不启用
    return
  }

  let image = nativeImage.createEmpty()
  try {
    // 从可执行文件提取图标：打包后即衡账应用图标，不引入新资源文件；
    // 平台不支持（部分 Linux）时留空图标，托盘仍可用（菜单可点）
    image = await app.getFileIcon(process.execPath, { size: 'small' })
  } catch {
    /* ignore */
  }
  tray = new Tray(image.isEmpty() ? nativeImage.createEmpty() : image)
  rebuildTrayMenu()

  // 关窗 = 隐藏到托盘；首次隐藏提示一次，防止用户以为程序退出（后台还在同步）
  let hinted = false
  BrowserWindow.getAllWindows().forEach((win) => attachHideOnClose(win))
  app.on('browser-window-created', (_e, win) => attachHideOnClose(win))

  function attachHideOnClose(win: BrowserWindow) {
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

  startAutoSyncTimer()
}

/** 托盘菜单（开机自启开关状态变化时重建） */
function rebuildTrayMenu() {
  if (!tray) return
  tray.setToolTip('衡账 · 金蝶采集器')
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: '显示主窗口', click: () => BrowserWindow.getAllWindows()[0]?.show() },
      { type: 'separator' },
      { label: '立即同步并上报', click: () => void autoSync('手动') },
      {
        label: `开机自启（当前：${app.getLoginItemSettings().openAtLogin ? '开' : '关'}）`,
        click: () => {
          const next = !app.getLoginItemSettings().openAtLogin
          app.setLoginItemSettings({ openAtLogin: next })
          rebuildTrayMenu()
        }
      },
      { type: 'separator' },
      {
        label: '退出',
        click: () => {
          markQuitting()
          app.quit()
        }
      }
    ])
  )
}

/** 启动定时同步 */
function startAutoSyncTimer() {
  if (autoTimer) return
  autoTimer = setInterval(() => void autoSync('定时'), AUTO_SYNC_HOURS * 3_600_000)
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
  try {
    // 业务锁在同步进行中会直接抛错（"已有同步或其它任务进行中"）——让位即可
    const results = await runSync({})
    const upload = await uploadToNova()
    tray?.displayBalloon?.({
      iconType: 'info',
      title: `同步完成（${trigger}）`,
      content: `${results.map((r) => r.message).join('；')}；上行：${upload.message}`
    })
  } catch (e) {
    // 同步失败不打扰业务员（托盘 tooltip 留痕），下个周期自动重试
    tray?.displayBalloon?.({
      iconType: 'warning',
      title: `同步未完成（${trigger}）`,
      content: e instanceof Error ? e.message : String(e)
    })
  }
}

/** 停止定时器（退出时调用） */
export function stopAutoSync() {
  if (autoTimer) clearInterval(autoTimer)
  autoTimer = undefined
}

/** 是否启用定时同步（预留：后续可加设置项） */
export function isAutoSyncEnabled(): boolean {
  return autoSyncEnabled
}
