import { BrowserWindow } from 'electron'
import { autoUpdater } from 'electron-updater'
import { canInstallUpdate, getLock, tryAcquire, release } from './businessLock'
import type { UpdateStatus } from '../shared/types'

autoUpdater.autoDownload = false
autoUpdater.autoInstallOnAppQuit = true

let status: UpdateStatus = {
  checking: false,
  available: false,
  downloading: false
}

function broadcast() {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('updater:status', status)
  }
}

export function getUpdateStatus() {
  return { ...status }
}

export function initUpdater() {
  autoUpdater.on('checking-for-update', () => {
    status = { ...status, checking: true, error: undefined }
    broadcast()
  })
  autoUpdater.on('update-available', (info) => {
    status = {
      ...status,
      checking: false,
      available: true,
      version: info.version,
      releaseNotes: typeof info.releaseNotes === 'string' ? info.releaseNotes : undefined
    }
    broadcast()
  })
  autoUpdater.on('update-not-available', () => {
    status = { ...status, checking: false, available: false }
    broadcast()
  })
  autoUpdater.on('error', (err) => {
    status = {
      ...status,
      checking: false,
      downloading: false,
      error: err.message
    }
    broadcast()
  })
  autoUpdater.on('download-progress', (p) => {
    status = { ...status, downloading: true, progress: p.percent }
    broadcast()
  })
  autoUpdater.on('update-downloaded', () => {
    status = { ...status, downloading: false, progress: 100 }
    broadcast()
  })
}

export async function checkForUpdatesSilent() {
  try {
    status = { ...status, checking: true, error: undefined }
    broadcast()
    await autoUpdater.checkForUpdates()
  } catch {
    status = { ...status, checking: false }
    broadcast()
  }
}

export async function checkForUpdatesManual() {
  status = { ...status, checking: true, error: undefined }
  broadcast()
  return autoUpdater.checkForUpdates()
}

export async function downloadUpdate() {
  if (!canInstallUpdate() && getLock() !== 'idle') {
    throw new Error('同步或导出进行中，请稍后再更新')
  }
  status = { ...status, downloading: true, progress: 0 }
  broadcast()
  return autoUpdater.downloadUpdate()
}

export function quitAndInstall() {
  if (!tryAcquire('update') && getLock() !== 'update') {
    throw new Error('有任务进行中，无法安装更新')
  }
  try {
    autoUpdater.quitAndInstall(false, true)
  } finally {
    release('update')
  }
}
