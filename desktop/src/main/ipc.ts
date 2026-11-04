import { ipcMain, shell } from 'electron'
import { getPublicConfig, saveConfig } from './configStore'
import { bindNova, novaState, novaUploadHistory, unbindNova, uploadToNova } from './nova/uploader'
import { uploadMapping } from './nova/client'
import { getNovaRuntime } from './configStore'
import { clearTaskCache, refreshTasks } from './nova/tasks'
import { startCommandLoop, stopCommandLoop } from './nova/commands'
import { getNovaHealth, probeNova } from './nova/health'
import { getKingdee } from './kingdee/client'
import { detectKingdeeEnv } from './kingdee/envDetect'
import { runM0Probe } from './kingdee/probe'
import { getDb } from './db'
import { runSync, listCustomers, lastSyncStats, syncHistory } from './sync/runner'
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
    const results = await runSync({ ...input, trigger: input.trigger || 'manual' })
    // 本地同步成功后再上行；不 await，避免业务员拿对账单前先等一遍网络
    // （上行结果由 uploader 统一广播 nova:upload，四个入口共用一条路径）
    void uploadToNova()
    return results
  })
  ipcMain.handle('sync:lastStats', () => lastSyncStats())
  ipcMain.handle('sync:history', () => syncHistory(200))
  ipcMain.handle('customers:list', () => listCustomers())

  // 上行同步（nova 对账平台）：渲染进程拿不到令牌原文，只能看状态
  ipcMain.handle('nova:state', () => novaState())
  ipcMain.handle('nova:history', () => novaUploadHistory(200))
  ipcMain.handle('nova:health', () => getNovaHealth())
  ipcMain.handle('nova:probe', () => probeNova())
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
    // 结果由 uploader 统一广播（覆盖手动 / 托盘 / 指令 / 同步后自动四个入口）
    return uploadToNova()
  })

  // 字段自检结果回传：把自检拼出的字段集写到本绑定的服务端字段映射，
  // 运维在网页上确认后下发 config.reload 生效
  ipcMain.handle(
    'nova:uploadMapping',
    async (
      _e,
      input: { lineFieldKeys?: string; customerPoField?: string; note?: string }
    ): Promise<{ ok: boolean; message: string }> => {
      const rt = getNovaRuntime()
      if (!rt.token || !rt.bindingId) {
        return { ok: false, message: '尚未绑定对账平台，请先在「连接设置」里绑定' }
      }
      try {
        await uploadMapping(rt.baseUrl, rt.token, input)
        return { ok: true, message: '已上传，运维在「同步配置」确认后即生效' }
      } catch (e) {
        return { ok: false, message: e instanceof Error ? e.message : String(e) }
      }
    }
  )

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
