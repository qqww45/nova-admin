import { app, BrowserWindow, shell } from 'electron'
import { join } from 'path'
import { registerIpc } from './ipc'
import { initUpdater, checkForUpdatesSilent } from './updater'
import { startCommandLoop, stopCommandLoop } from './nova/commands'
import { refreshTasks } from './nova/tasks'
import { closeDb } from './db'
import { getUserRoots } from './paths'
import { initTray, markQuitting, stopAutoSync } from './tray'

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
    win.show()
  })

  win.webContents.setWindowOpenHandler((details) => {
    shell.openExternal(details.url)
    return { action: 'deny' }
  })

  if (isDev() && process.env['ELECTRON_RENDERER_URL']) {
    win.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    win.loadFile(join(__dirname, '../renderer/index.html'))
  }
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

  // 托盘常驻 + 定时同步 + 开机自启（仅打包后；开发模式关窗即退）
  if (app.isPackaged) {
    void initTray(() => BrowserWindow.getAllWindows()[0] ?? null)
    // 业务员电脑重启后采集器自动拉起（方案 §8）；托盘菜单可随时关掉
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
