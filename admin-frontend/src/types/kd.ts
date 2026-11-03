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
  ownerUserName?: string;
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

/** 对账单数据体检：解释"本期为什么对账单少数据 / 金额对不上" */
export interface KdStatementHealthResp {
  dateFrom?: string;
  dateTo?: string;
  /** 有效出库单数（未作废、未失效） */
  billCount: number;
  /** 无分录数据的单数（对账单退回表头金额） */
  noLineCount: number;
  /** 作废单数 */
  cancelledCount: number;
  /** 已失效单数（金蝶侧物理删除） */
  invalidCount: number;
  /** 全部健康（无分录/作废/失效均为 0） */
  healthy: boolean;
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

/* ============ 同步数据查看（排查页，M5） ============ */

/** 数据归属人（业务员）下拉项 */
export interface KdOwnerResp {
  id: string;
  name: string;
}

/** 客户数据查询条件 */
export interface KdCustomerDataQuery {
  acctId?: string;
  /** 客户名称 / 金蝶客户编码 */
  keyword?: string;
  /** 0 启用 / 1 禁用 */
  disabled?: number;
  batchNo?: string;
  /** yyyy-MM-dd（含） */
  syncDateFrom?: string;
  syncDateTo?: string;
  /** 仅管理员生效 */
  ownerUserId?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 客户数据行 */
export interface KdCustomerDataResp {
  id: string;
  ownerUserId: string;
  ownerUserName?: string;
  acctId: string;
  kdFid: string;
  kdNumber?: string;
  customerName?: string;
  address?: string;
  disabled?: number;
  batchNo?: string;
  syncAt?: string;
}

/** 出库单数据查询条件 */
export interface KdOutboundDataQuery {
  acctId?: string;
  /** 单据编号 / 客户名称 */
  keyword?: string;
  /** yyyy-MM-dd（含） */
  dateFrom?: string;
  dateTo?: string;
  /** A 创建 / B 审核中 / C 已审核 / D 重新审核 */
  docStatus?: string;
  /** 0 正常 / 1 已作废 */
  cancelled?: number;
  /** 0 正常 / 1 已失效（全量差集标记） */
  invalid?: number;
  batchNo?: string;
  /** 仅管理员生效 */
  ownerUserId?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 出库单数据行 */
export interface KdOutboundDataResp {
  id: string;
  ownerUserId: string;
  ownerUserName?: string;
  acctId: string;
  kdFid: string;
  billNo?: string;
  bizDate?: string;
  customerFid?: string;
  customerName?: string;
  docStatus?: string;
  docStatusLabel?: string;
  cancelStatus?: string;
  cancelled?: number;
  /** 元 */
  amount?: number;
  /** 0 = 该单无分录数据（对账单退回表头金额） */
  lineCount?: number;
  remark?: string;
  kdModifyTime?: string;
  invalid?: number;
  batchNo?: string;
  syncAt?: string;
}

/** 出库分录查询条件 */
export interface KdLineDataQuery {
  acctId?: string;
  /** 金蝶出库单 FID */
  outboundFid?: string;
  /** 物料编码 / 物料名称 / 源单号 / 客户PO */
  keyword?: string;
  /** 0 正常 / 1 已失效 */
  invalid?: number;
  batchNo?: string;
  /** yyyy-MM-dd（含） */
  syncDateFrom?: string;
  syncDateTo?: string;
  /** 仅管理员生效 */
  ownerUserId?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 出库分录数据行 */
export interface KdOutboundLineDataResp {
  id: string;
  ownerUserId: string;
  ownerUserName?: string;
  acctId: string;
  outboundFid: string;
  /** 服务端按「账套 + FID」回填 */
  billNo?: string;
  entryId: string;
  materialNumber?: string;
  materialName?: string;
  spec?: string;
  unit?: string;
  qty?: number;
  price?: number;
  amount?: number;
  srcOrderNo?: string;
  customerPo?: string;
  remark?: string;
  invalid?: number;
  batchNo?: string;
  syncAt?: string;
}
