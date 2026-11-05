import { BrowserWindow } from 'electron'
import { autoUpdater } from 'electron-updater'
import { canInstallUpdate, getLock, tryAcquire, release } from './businessLock'
import type { UpdateStatus } from '../shared/types'

autoUpdater.autoDownload = false
autoUpdater.autoInstallOnAppQuit = true

// 临时覆盖发布源（联调 / 迁移服务器用）。包内 app-update.yml 的地址仍以 build.publish 为准。
const overrideFeedUrl = process.env['HENGZHANG_UPDATE_URL']
if (overrideFeedUrl) {
  autoUpdater.setFeedURL({ provider: 'generic', url: overrideFeedUrl })
}

/**
 * electron-updater 的原始报错是一段英文 HTTP/网络错误串，404 时还会带整段响应头，
 * 业务员看不懂。这里翻译成一句话中文，并保留截断后的原始信息便于运维排查。
 */
export function describeUpdateError(raw: string): string {
  const brief = raw.replace(/\s+/g, ' ').trim()
  const detail = brief.length > 160 ? `${brief.slice(0, 160)}…` : brief
  const statusMatch = brief.match(/(?:HttpError|status[:\s])(?:\s*:?\s*)?(\d{3})/)
  const urlMatch = brief.match(/url:\s*(\S+)/)
  const url = urlMatch?.[1] ?? ''
  const code = statusMatch?.[1]

  if (code === '404' && /github\.com/.test(url)) {
    return `GitHub Releases 上没有找到更新文件（404）。新版本可能尚未发布，请稍后重试，或从发布页下载完整安装包。 [${detail}]`
  }
  if (code === '404') {
    return `更新源上没有找到更新文件（404）：新版本可能尚未发布，或发布地址配置有误。请稍后重试，或联系运维。 [${detail}]`
  }
  if (code === '401' || code === '403') {
    return `更新源拒绝访问（${code}），请联系运维检查发布源的访问权限。 [${detail}]`
  }
  if (code && code.startsWith('5')) {
    return `更新服务暂时不可用（${code}），请稍后重试。 [${detail}]`
  }
  if (/ERR_NAME_NOT_RESOLVED|ENOTFOUND/.test(brief)) {
    return `无法解析更新服务器的域名，请检查网络后重试。 [${detail}]`
  }
  if (/ERR_CONNECTION_REFUSED|ECONNREFUSED/.test(brief)) {
    return `无法连接更新服务器，请检查网络后重试。 [${detail}]`
  }
  if (/ERR_INTERNET_DISCONNECTED|NETWORK_CHANGED/.test(brief)) {
    return `网络未连接或已断开，请恢复网络后重试。 [${detail}]`
  }
  if (/ERR_TIMED_OUT|ETIMEDOUT|timeout/i.test(brief)) {
    return `连接更新服务器超时，请稍后重试。 [${detail}]`
  }
  return `检查更新失败：${detail}`
}

function rawErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

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
      error: describeUpdateError(rawErrorMessage(err))
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
  try {
    return await autoUpdater.checkForUpdates()
  } catch (err) {
    // 手动「检查更新」走 IPC 抛回渲染进程，翻译成中文；静默检查（error 事件）同样会收到该消息
    const message = describeUpdateError(rawErrorMessage(err))
    status = { ...status, checking: false, error: message }
    broadcast()
    throw new Error(message)
  }
}

export async function downloadUpdate() {
  if (!canInstallUpdate() && getLock() !== 'idle') {
    throw new Error('同步或导出进行中，请稍后再更新')
  }
  status = { ...status, downloading: true, progress: 0 }
  broadcast()
  try {
    return await autoUpdater.downloadUpdate()
  } catch (err) {
    const message = describeUpdateError(rawErrorMessage(err))
    status = { ...status, downloading: false, error: message }
    broadcast()
    throw new Error(message)
  }
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
