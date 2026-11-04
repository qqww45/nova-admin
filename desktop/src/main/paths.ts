import { app } from 'electron'
import { join } from 'path'
import { existsSync, mkdirSync } from 'fs'

export function getUserRoots() {
  const root = app.getPath('userData')
  const dirs = {
    root,
    config: join(root, 'config'),
    db: join(root, 'db'),
    backups: join(root, 'backups'),
    exports: join(root, 'exports'),
    logs: join(root, 'logs'),
    mappings: join(root, 'mappings')
  }
  for (const d of Object.values(dirs)) {
    if (!existsSync(d)) mkdirSync(d, { recursive: true })
  }
  return dirs
}

export function getResourcesRoot() {
  if (app.isPackaged) {
    return join(process.resourcesPath, 'resources')
  }
  return join(app.getAppPath(), 'resources')
}

/** 窗口 / 托盘用 PNG；安装包用同目录 icon.ico */
export function getAppIconPath(file: 'icon.png' | 'icon.ico' = 'icon.png') {
  return join(getResourcesRoot(), file)
}
