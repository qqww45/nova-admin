import { app, BrowserWindow, shell } from 'electron'
import { join } from 'path'
import { registerIpc } from './ipc'
import { initUpdater, checkForUpdatesSilent } from './updater'
import { startCommandLoop, stopCommandLoop } from './nova/commands'
import { refreshTasks } from './nova/tasks'
import { closeDb } from './db'
import { getUserRoots } from './paths'
import { initTray, markQuitting, stopAutoSync } from './tray'
import { preferIpv4Loopback, waitForHttpOk, buildStartupErrorHtml } from './devRenderer'

function isDev() {
  return !app.isPackaged
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 840,
    minWidth: 1100,
    minHeight: 720,
    show: false,
    title: '衡账',
    backgroundColor: '#0B1220',
    titleBarStyle: 'hidden',
    titleBarOverlay: {
      color: '#0B1220',
      symbolColor: '#E8EEF7',
      height: 36
    },
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  win.on('ready-to-show', () => {
    if (!win.isDestroyed()) win.show()
  })

  win.webContents.setWindowOpenHandler((details) => {
    shell.openExternal(details.url)
    return { action: 'deny' }
  })

  void loadRenderer(win)
}

/** 开发态等 Vite 可连再 load；失败则显示说明页，避免只剩标题栏底色 */
async function loadRenderer(win: BrowserWindow) {
  if (isDev() && process.env['ELECTRON_RENDERER_URL']) {
    const url = preferIpv4Loopback(process.env['ELECTRON_RENDERER_URL'])
    const ready = await waitForHttpOk(url)
    if (win.isDestroyed()) return
    if (!ready) {
      await showDevStartupError(win, url, 'ERR_CONNECTION_REFUSED：开发服务器未在该地址监听。')
      return
    }
    try {
      await win.loadURL(url)
    } catch (e) {
      if (win.isDestroyed()) return
      await showDevStartupError(win, url, e instanceof Error ? e.message : String(e))
    }
    return
  }
  await win.loadFile(join(__dirname, '../renderer/index.html'))
}

async function showDevStartupError(win: BrowserWindow, url: string, error: string) {
  await win.loadURL(
    'data:text/html;charset=utf-8,' + encodeURIComponent(buildStartupErrorHtml({ url, error }))
  )
  if (!win.isDestroyed()) win.show()
}

app.whenReady().then(() => {
  getUserRoots()
  registerIpc()
  initUpdater()
  createWindow()
  // 已绑定时开始拉指令（内部自检令牌，未绑定则空转）
  startCommandLoop()
  // 启动即拉一次任务清单：字段集配置可能已被运维改过
  void refreshTasks()

  // 托盘常驻 + 关窗隐藏 + 定时同步（开发模式同样启用，便于验证托盘行为；
  // 系统不支持托盘时自动退回"关窗即退"）
  void initTray()

  // 业务员电脑重启后采集器自动拉起（方案 §8）；托盘菜单可随时关掉（仅打包后）
  if (app.isPackaged) {
    app.setLoginItemSettings({ openAtLogin: true })
  }

  setTimeout(() => {
    if (app.isPackaged) void checkForUpdatesSilent()
  }, 2500)

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  closeDb()
  if (process.platform !== 'darwin') app.quit()
})

// 退出前停掉轮询与定时同步，避免定时器在 app 销毁后继续发请求
app.on('before-quit', () => {
  markQuitting()
  stopCommandLoop()
  stopAutoSync()
})
