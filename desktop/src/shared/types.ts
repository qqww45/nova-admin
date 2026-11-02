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

export interface SyncStartInput {
  sinceDate: string
}

export interface LastSyncStat {
  entity: SyncEntity
  status: SyncRunStatus
  rowCount: number
  message: string
  finishedAt?: string | null
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
