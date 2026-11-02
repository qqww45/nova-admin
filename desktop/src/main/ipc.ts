import { BrowserWindow, ipcMain, shell } from 'electron'
import { getPublicConfig, saveConfig } from './configStore'
import { bindNova, novaState, unbindNova, uploadToNova } from './nova/uploader'
import { clearTaskCache, refreshTasks } from './nova/tasks'
import { startCommandLoop, stopCommandLoop } from './nova/commands'
import { getKingdee } from './kingdee/client'
import { detectKingdeeEnv } from './kingdee/envDetect'
import { runM0Probe } from './kingdee/probe'
import { getDb } from './db'
import { runSync, listCustomers, lastSyncStats } from './sync/runner'
import { buildStatement } from './domain/statement'
import { exportStatementXlsx } from './export/excel'
import {
  hasUserMapping,
  loadOutstockMapping,
  saveUserOutstockMapping,
  clearUserOutstockMapping,
  userMappingPath
} from './mappingStore'
import {
  checkForUpdatesManual,
  downloadUpdate,
  getUpdateStatus,
  quitAndInstall
} from './updater'
import type {
  AppConfigSaveInput,
  MappingApplyInput,
  NovaBindInput,
  NovaUploadResult,
  ProbeReport,
  StatementPreviewQuery,
  SyncStartInput
} from '../shared/types'
import { app } from 'electron'
import { getUserRoots } from './paths'

export function registerIpc() {
  getDb()

  ipcMain.handle('app:getInfo', () => ({
    version: app.getVersion(),
    productName: '衡账',
    userDataPath: getUserRoots().root
  }))

  ipcMain.handle('config:get', () => getPublicConfig())
  ipcMain.handle('config:save', (_e, input: AppConfigSaveInput) => saveConfig(input))
  ipcMain.handle('config:detectEnv', () => detectKingdeeEnv())
  ipcMain.handle('config:test', async () => {
    const client = getKingdee()
    const login = await client.login()
    if (!login.ok) return { ok: false, message: login.message }
    const probe = await client.probeOutboundSample()
    return probe
  })

  // 与「测试连接」彼此独立：登录 + 抽样三个单据 + 逐套试出库分录字段集
  ipcMain.handle('config:probe', async (): Promise<ProbeReport> => {
    const client = getKingdee()
    const login = await client.login()
    if (!login.ok) {
      return {
        ok: false,
        loginMessage: login.message,
        entities: [],
        lineFieldSets: [],
        finishedAt: new Date().toISOString()
      }
    }
    return runM0Probe()
  })

  ipcMain.handle('mapping:get', () => ({
    mapping: loadOutstockMapping(),
    path: userMappingPath(),
    userOverride: hasUserMapping()
  }))

  ipcMain.handle('mapping:apply', (_e, input: MappingApplyInput) => ({
    mapping: saveUserOutstockMapping(input),
    path: userMappingPath(),
    userOverride: hasUserMapping()
  }))
  ipcMain.handle('mapping:reset', () => {
    clearUserOutstockMapping()
    return { mapping: loadOutstockMapping(), path: userMappingPath(), userOverride: false }
  })

  ipcMain.handle('sync:run', async (_e, input: SyncStartInput) => {
    const results = await runSync(input)
    // 本地同步成功后再上行；不 await，避免业务员拿对账单前先等一遍网络
    void autoUpload()
    return results
  })
  ipcMain.handle('sync:lastStats', () => lastSyncStats())
  ipcMain.handle('customers:list', () => listCustomers())

  // 上行同步（nova 对账平台）：渲染进程拿不到令牌原文，只能看状态
  ipcMain.handle('nova:state', () => novaState())
  ipcMain.handle('nova:bind', async (_e, input: NovaBindInput) => {
    const result = await bindNova(input)
    if (result.ok) {
      // 绑定后立刻拉一次任务清单并开始轮询指令，业务员不用重启客户端
      void refreshTasks(true)
      startCommandLoop()
    }
    return result
  })
  ipcMain.handle('nova:unbind', () => {
    stopCommandLoop()
    clearTaskCache()
    return unbindNova()
  })
  ipcMain.handle('nova:upload', async (): Promise<NovaUploadResult> => {
    const result = await uploadToNova()
    broadcastNova(result)
    return result
  })

  ipcMain.handle('statement:preview', (_e, q: StatementPreviewQuery) =>
    buildStatement(q.customerFid, q.fromMonth, q.toMonth)
  )

  ipcMain.handle('export:xlsx', async (_e, q: StatementPreviewQuery) => {
    const dto = buildStatement(q.customerFid, q.fromMonth, q.toMonth)
    const path = await exportStatementXlsx(dto)
    return { path }
  })

  ipcMain.handle('shell:showItem', (_e, filePath: string) => shell.showItemInFolder(filePath))
  ipcMain.handle('shell:openPath', (_e, filePath: string) => shell.openPath(filePath))

  ipcMain.handle('updater:status', () => getUpdateStatus())
  ipcMain.handle('updater:check', () => checkForUpdatesManual())
  ipcMain.handle('updater:download', () => downloadUpdate())
  ipcMain.handle('updater:install', () => {
    quitAndInstall()
  })
}

/** 上行结果广播到所有窗口（同步页据此刷新状态） */
function broadcastNova(result: NovaUploadResult) {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('nova:upload', result)
  }
}

/** 同步结束后自动上行：失败只记录，不打扰业务员 */
async function autoUpload() {
  try {
    broadcastNova(await uploadToNova())
  } catch (e) {
    broadcastNova({
      ok: false,
      status: 'failed',
      message: e instanceof Error ? e.message : String(e)
    })
  }
}
