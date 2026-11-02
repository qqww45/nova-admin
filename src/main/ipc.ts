import { ipcMain, shell } from 'electron'
import { getPublicConfig, saveConfig } from './configStore'
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

  ipcMain.handle('sync:run', async (_e, input: SyncStartInput) => runSync(input))
  ipcMain.handle('sync:lastStats', () => lastSyncStats())
  ipcMain.handle('customers:list', () => listCustomers())

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
