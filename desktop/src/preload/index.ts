import { contextBridge, ipcRenderer } from 'electron'
import type {
  AppConfigPublic,
  AppConfigSaveInput,
  AppInfo,
  EnvDetectResult,
  ConnectionTestResult,
  CustomerOption,
  MappingApplyInput,
  MappingState,
  NovaBindInput,
  NovaBindResult,
  NovaState,
  NovaUploadResult,
  ProbeReport,
  LastSyncStat,
  StatementDTO,
  StatementPreviewQuery,
  SyncProgress,
  SyncStartInput,
  UpdateStatus
} from '../shared/types'

const api = {
  getInfo: (): Promise<AppInfo> => ipcRenderer.invoke('app:getInfo'),
  getConfig: (): Promise<AppConfigPublic> => ipcRenderer.invoke('config:get'),
  saveConfig: (input: AppConfigSaveInput): Promise<AppConfigPublic> =>
    ipcRenderer.invoke('config:save', input),
  detectEnv: (): Promise<EnvDetectResult> => ipcRenderer.invoke('config:detectEnv'),
  testConnection: (): Promise<ConnectionTestResult> => ipcRenderer.invoke('config:test'),
  runProbe: (): Promise<ProbeReport> => ipcRenderer.invoke('config:probe'),
  getMapping: (): Promise<MappingState> => ipcRenderer.invoke('mapping:get'),
  applyMapping: (input: MappingApplyInput): Promise<MappingState> =>
    ipcRenderer.invoke('mapping:apply', input),
  resetMapping: (): Promise<MappingState> => ipcRenderer.invoke('mapping:reset'),
  runSync: (input: SyncStartInput): Promise<SyncProgress[]> => ipcRenderer.invoke('sync:run', input),
  lastSyncStats: (): Promise<LastSyncStat[]> => ipcRenderer.invoke('sync:lastStats'),
  onSyncProgress: (cb: (p: SyncProgress) => void) => {
    const listener = (_: Electron.IpcRendererEvent, p: SyncProgress) => cb(p)
    ipcRenderer.on('sync:progress', listener)
    return () => ipcRenderer.removeListener('sync:progress', listener)
  },
  listCustomers: (): Promise<CustomerOption[]> => ipcRenderer.invoke('customers:list'),
  getNovaState: (): Promise<NovaState> => ipcRenderer.invoke('nova:state'),
  bindNova: (input: NovaBindInput): Promise<NovaBindResult> => ipcRenderer.invoke('nova:bind', input),
  unbindNova: (): Promise<NovaState> => ipcRenderer.invoke('nova:unbind'),
  uploadToNova: (): Promise<NovaUploadResult> => ipcRenderer.invoke('nova:upload'),
  onNovaUpload: (cb: (r: NovaUploadResult) => void) => {
    const listener = (_: Electron.IpcRendererEvent, r: NovaUploadResult) => cb(r)
    ipcRenderer.on('nova:upload', listener)
    return () => ipcRenderer.removeListener('nova:upload', listener)
  },
  previewStatement: (q: StatementPreviewQuery): Promise<StatementDTO> =>
    ipcRenderer.invoke('statement:preview', q),
  exportXlsx: (q: StatementPreviewQuery): Promise<{ path: string }> =>
    ipcRenderer.invoke('export:xlsx', q),
  showItemInFolder: (path: string) => ipcRenderer.invoke('shell:showItem', path),
  openPath: (path: string) => ipcRenderer.invoke('shell:openPath', path),
  getUpdateStatus: (): Promise<UpdateStatus> => ipcRenderer.invoke('updater:status'),
  checkUpdate: () => ipcRenderer.invoke('updater:check'),
  downloadUpdate: () => ipcRenderer.invoke('updater:download'),
  installUpdate: () => ipcRenderer.invoke('updater:install'),
  onUpdateStatus: (cb: (s: UpdateStatus) => void) => {
    const listener = (_: Electron.IpcRendererEvent, s: UpdateStatus) => cb(s)
    ipcRenderer.on('updater:status', listener)
    return () => ipcRenderer.removeListener('updater:status', listener)
  }
}

contextBridge.exposeInMainWorld('hengzhang', api)

export type HengZhangApi = typeof api
