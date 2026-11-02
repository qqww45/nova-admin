/**
 * 金蝶对账同步（采集器上行）相关类型。
 *
 * <p>与后端 interfaces/dto 下的 Kd* DTO 一一对应。
 * 金额单位：分；数量单位：千分之一（与桌面端 amount_cents / qty_milli 一致）。
 */

/** 采集器绑定 */
export interface KdBindingResp {
  id: string;
  userId: string;
  userName: string;
  acctId: string;
  acctName?: string;
  kdUsername?: string;
  kdServerUrl?: string;
  machineName?: string;
  clientVersion?: string;
  tokenPrefix?: string;
  /** ACTIVE / REVOKED */
  status: string;
  statusLabel: string;
  lastUsedAt?: string;
  lastSyncAt?: string;
  lastSyncResult?: string;
  watermarkCustomer?: string;
  watermarkOutbound?: string;
  customerCount?: number;
  outboundCount?: number;
  createTime?: string;
}

/** 一次性绑定码 */
export interface KdBindingCodeResp {
  code: string;
  expireAt: string;
  expireSeconds: number;
}

/** 换取到的机器令牌 */
export interface KdBindingRedeemResp {
  token: string;
  bindingId: string;
  userId: string;
  acctId: string;
  acctName?: string;
  watermarkCustomer?: string;
  watermarkOutbound?: string;
}

/** 同步任务 */
export interface KdSyncTaskResp {
  id: string;
  acctId: string;
  entity: string;
  formId: string;
  endpoint: string;
  fieldKeys: string;
  watermarkField?: string;
  enabled: number;
  sortNo: number;
  remark?: string;
  updateTime?: string;
}

/** 同步任务保存请求 */
export interface KdTaskSaveReq {
  id?: string;
  acctId?: string;
  entity: string;
  formId: string;
  endpoint: string;
  fieldKeys: string;
  watermarkField?: string;
  enabled?: number;
  sortNo?: number;
  remark?: string;
}

/** 账套字段映射 */
export interface KdFieldMappingResp {
  id: string;
  bindingId: string;
  mappingKey: string;
  mappingValue?: string;
  remark?: string;
}

/** 字段映射保存请求 */
export interface KdFieldMappingSaveReq {
  bindingId: string;
  mappingKey: string;
  mappingValue?: string;
  remark?: string;
}

/** 同步批次 */
export interface KdSyncBatchResp {
  id: string;
  batchNo: string;
  bindingId: string;
  ownerUserId: string;
  acctId: string;
  entity?: string;
  mode?: string;
  status: string;
  rowCount?: number;
  upsertCount?: number;
  skippedCount?: number;
  watermarkFrom?: string;
  watermarkTo?: string;
  startedAt?: string;
  finishedAt?: string;
  costMs?: number;
  clientVersion?: string;
  message?: string;
  createTime?: string;
}

/** 批次查询条件 */
export interface KdBatchQuery {
  entity?: string;
  status?: string;
  acctId?: string;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 对账单查询条件 */
export interface KdStatementQuery {
  acctId?: string;
  customerFid?: string;
  /** yyyy-MM-dd */
  dateFrom?: string;
  /** yyyy-MM-dd */
  dateTo?: string;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 对账单汇总行 */
export interface KdStatementSummaryResp {
  acctId?: string;
  customerFid?: string;
  customerName?: string;
  billCount: number;
  /** 元 */
  totalAmount: number;
  firstDate?: string;
  lastDate?: string;
}

/** 对账单分录 */
export interface KdStatementLineResp {
  entryId?: string;
  materialNumber?: string;
  materialName?: string;
  spec?: string;
  unit?: string;
  qty: number;
  price: number;
  amount: number;
  srcOrderNo?: string;
  customerPo?: string;
  remark?: string;
}

/** 对账单单据 */
export interface KdStatementBillResp {
  customerName?: string;
  billNo?: string;
  bizDate?: string;
  docStatus?: string;
  amount: number;
  lines: KdStatementLineResp[];
}

/** 对账单明细 */
export interface KdStatementDetailResp {
  acctId?: string;
  customerFid?: string;
  customerName?: string;
  dateFrom?: string;
  dateTo?: string;
  billCount: number;
  lineCount: number;
  totalAmount: number;
  bills: KdStatementBillResp[];
}

/** 采集器指令 */
export interface KdCommandResp {
  id: string;
  bindingId?: string;
  command: string;
  payload?: string;
  /** PENDING / SENT / DONE / EXPIRED */
  status: string;
  result?: string;
  createTime?: string;
  deliverTime?: string;
  doneTime?: string;
}
