export type SyncEntity =
  | 'customer'
  | 'sale_order'
  | 'outbound'
  | 'return'
  | 'payment'
  | 'invoice'

export type SyncRunStatus = 'running' | 'success' | 'failed' | 'skipped_no_perm'

export interface AppConfigPublic {
  serverUrl: string
  acctId: string
  acctName?: string
  username: string
  hasPassword: boolean
  companyName: string
  companyShortName: string
  companyAddress: string
  companyPhone: string
  preparerName: string
  language: number
}

export interface AppConfigSaveInput {
  serverUrl: string
  acctId: string
  acctName?: string
  username: string
  password?: string
  clearPassword?: boolean
  companyName: string
  companyShortName: string
  companyAddress: string
  companyPhone: string
  preparerName: string
}

export interface ConnectionTestResult {
  ok: boolean
  message: string
  kickedHint?: boolean
}

export interface ProbeEntityResult {
  name: string
  label: string
  formId: string
  ok: boolean
  rowCount: number
  message?: string
  sample?: string[]
}

export interface ProbeFieldCheck {
  field: string
  ok: boolean
  error?: string
}

export interface MetaField {
  key: string
  name: string
}

export interface MetaProbeResult {
  formId: string
  service?: string
  total: number
  fields: MetaField[]
  error?: string
}

export interface BillFieldsResult {
  formId: string
  total: number
  /** 自定义字段（F_xxx）及其实际值 */
  custom: FieldValue[]
  /** 疑似采购订单号的字段 */
  poHints: FieldValue[]
  error?: string
}

export interface FieldValue {
  key: string
  value: string
}

export interface ProbeMeta {
  saleOrder?: MetaProbeResult
  outstock?: MetaProbeResult
  /** 疑似「客户采购订单号」的字段 */
  poCandidates: MetaField[]
  /** 元数据服务不可用时的替代方案：真实单据字段值 */
  billFields?: {
    saleOrder?: BillFieldsResult
    outstock?: BillFieldsResult
  }
}

export interface ProbeFieldSetResult {
  index: number
  fieldKeys: string
  ok: boolean
  error?: string
  rowCount?: number
  sample?: string[]
  /** 每个单元格的类型描述，用于定位返回对象的字段 */
  sampleTypes?: string[]
  /** 字段与单元格类型/取值的对应关系，便于核对映射 */
  sampleByField?: Array<{ field: string; type: string; value: string }>
}

export interface ProbeReport {
  ok: boolean
  loginMessage: string
  entities: ProbeEntityResult[]
  /** 分录字段逐个验证结果 */
  fieldChecks?: ProbeFieldCheck[]
  /** 元数据探测（字段名 + 中文标题），用于确认数量/金额/采购订单号真实字段名 */
  meta?: ProbeMeta
  lineFieldSets: ProbeFieldSetResult[]
  suggestedLineFieldKeys?: string
  customerPoField?: string
  mappingPath?: string
  finishedAt: string
}

export interface MappingApplyInput {
  lineFieldKeys?: string
  customerPoField?: string
}

/** 字段自检结果回传（上传到对账平台的 kd_field_mapping） */
export interface NovaMappingUploadInput {
  lineFieldKeys?: string
  customerPoField?: string
  note?: string
}

export interface MappingState {
  mapping: OutstockMappingView
  path: string
  userOverride: boolean
}

export interface OutstockMappingView {
  formId?: string
  fieldKeys?: string
  lineFieldKeys?: string
  customerPoField?: string
}

export interface DataCenterOption {
  id: string
  name: string
  number?: string
}

export interface EnvDetectResult {
  serverUrl: string
  acctId: string
  username: string
  dataCenters: DataCenterOption[]
  message: string
}

export interface SyncProgress {
  entity: SyncEntity
  status: SyncRunStatus
  rowCount: number
  message: string
  finishedAt?: string
}

/** 同步触发来源：页面手动 / 托盘定时 / 平台远程指令 */
export type SyncTrigger = 'manual' | 'auto' | 'command'

export interface SyncStartInput {
  /**
   * 显式重拉起点（yyyy-MM-dd）。空 = 自动模式：
   * 有金蝶修改水位 → 双水位增量（FModifyDate − 15min + 月度 FDate 回看）；
   * 无水位 → 全量（首次同步）。
   */
  sinceDate?: string
  /** 由主进程入口填写（手动 / 托盘 / 指令），渲染进程无需传 */
  trigger?: SyncTrigger
}

export interface LastSyncStat {
  entity: SyncEntity
  status: SyncRunStatus
  rowCount: number
  message: string
  finishedAt?: string | null
}

/** sync_run 表的一条历史记录（同步历史展示用） */
export interface SyncRunRecord {
  id: number
  entity: string
  status: SyncRunStatus
  rowCount: number
  message: string
  startedAt: string | null
  finishedAt: string | null
  /** 触发来源；老数据为 null */
  source: SyncTrigger | null
}

export interface CustomerOption {
  fid: number
  fnumber: string
  fname: string
  address?: string
}

export interface StatementPreviewQuery {
  customerFid: number
  fromMonth: string
  toMonth: string
}

export interface StatementLine {
  date: string
  deliveryNo: string
  purchaseOrderNo: string
  salesOrderNo: string
  materialNumber: string
  materialName: string
  unit: string
  qty: number
  price: number
  amount: number
  remark?: string
  remark2?: string
  source: 'kingdee' | 'manual'
}

export interface MonthFooter {
  month: string
  statementAmount: number
  paidAmount: number
  unpaidAmount: number
  invoicedAmount: number
  uninvoicedAmount: number
  note?: string
  paidSource: 'manual' | 'kingdee_hint'
  invoicedSource: 'manual' | 'kingdee_hint'
}

export interface StatementSheet {
  monthLabel: string
  monthKey: string
  lines: StatementLine[]
  totalAmount: number
  footer: MonthFooter
  cumulative: MonthFooter[]
}

export interface StatementDTO {
  companyName: string
  companyAddress: string
  companyPhone: string
  customerName: string
  customerAddress: string
  preparerName: string
  sheets: StatementSheet[]
}

export interface UpdateStatus {
  checking: boolean
  available: boolean
  version?: string
  releaseNotes?: string
  downloading: boolean
  progress?: number
  error?: string
}

export interface AppInfo {
  version: string
  productName: string
  userDataPath: string
}

/* ---------------- nova 服务器（对账平台）上行同步 ---------------- */

/** 上行状态（不含令牌原文） */
export interface NovaState {
  /** 对账平台地址，如 https://nova-api.s.ci64.com */
  baseUrl: string
  /** 是否已绑定（拿到机器令牌） */
  bound: boolean
  bindingId?: string
  userId?: string
  acctId?: string
  acctName?: string
  /** 上次成功上报时间 */
  lastUploadAt?: string
  /** 上次上报结果摘要 */
  lastUploadResult?: string
  /** 本地上行水位（只有该时间之后变化的行才会被上报） */
  uploadWatermark?: string
  /** 待上报行数（客户 + 出库单 + 分录） */
  pendingRows?: number
}

export interface NovaBindInput {
  /** 对账平台地址；留空沿用已保存值 */
  baseUrl: string
  /** nova 页面生成的 8 位绑定码 */
  code: string
}

export interface NovaBindResult {
  ok: boolean
  message: string
  state: NovaState
}

export type NovaUploadStatus = 'unbound' | 'noop' | 'success' | 'failed'

export interface NovaUploadResult {
  ok: boolean
  status: NovaUploadStatus
  message: string
  received?: number
  upserted?: number
  skipped?: number
  finishedAt?: string
}

/** nova_upload_run 表的一条历史记录（上行历史展示用） */
export interface NovaUploadRecord {
  id: number
  batchId: string | null
  status: 'success' | 'failed' | 'noop'
  message: string
  received: number
  upserted: number
  skipped: number
  startedAt: string | null
  finishedAt: string | null
}

/* ---------------- 对账平台健康状态 ---------------- */

/** 主动探测结果（点击「立即检测」时产生） */
export interface NovaProbeResult {
  ok: boolean
  /** unbound = 未绑定；ok = 服务正常；error = 请求失败 */
  status: 'ok' | 'error' | 'unbound'
  message: string
  latencyMs?: number
  checkedAt: string
}

/** 指令轮询（心跳）状态 */
export interface NovaHeartbeat {
  /** 轮询是否在跑（绑定后启动，解绑 / 退出时停止） */
  active: boolean
  pollIntervalSec: number
  /** 上次轮询成功时间 */
  lastOkAt?: string
  /** 上次轮询失败时间 */
  lastFailAt?: string
  lastFailMessage?: string
}

/** 托盘定时同步状态 */
export interface AutoSyncStatus {
  enabled: boolean
  intervalHours: number
  /** 启动后首次自动同步的宽限（秒）；从未跑过时用 */
  startupGraceSec?: number
  /** 上次自动同步时间（没有则查 sync_run 里最近一次 auto 记录） */
  lastRunAt?: string
  lastRunOk?: boolean
  lastRunMessage?: string
  /** 下次预计执行时间（墙钟；重启后按上次 auto 记录或启动宽限重算） */
  nextRunAt?: string
}

/** 对账平台综合健康状态（nova:health 返回） */
export interface NovaHealth {
  state: NovaState
  heartbeat: NovaHeartbeat
  /** 最近一次主动探测结果；未探测过则无 */
  lastProbe?: NovaProbeResult
  autoSync: AutoSyncStatus
}
