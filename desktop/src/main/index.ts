import { app, BrowserWindow, shell } from 'electron'
import { join } from 'path'
import { registerIpc } from './ipc'
import { initUpdater, checkForUpdatesSilent } from './updater'
import { closeDb } from './db'
import { getUserRoots } from './paths'

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
