/**
 * 客户 / 订单 / 出货 业务模型类型定义。
 *
 * <p>与后端 interfaces/dto 一一对应。单独成文件（而不是塞进 api.ts）的原因：
 * 这一块字段多且独立演进，混在一起会让 api.ts 变成上千行的"类型垃圾场"。
 */

/* ==================== 通用 ==================== */

/** 字典项（税率、币种、结算方式等，禁止前端硬编码） */
export interface DictResp {
  dictCode: string;
  dictValue: string;
  dictLabel: string;
  sort?: number;
}

/** 批量操作逐行结果 */
export interface BatchLineResult {
  lineNo: number;
  success: boolean;
  bizId?: string;
  message?: string;
  errorField?: string;
  rawData?: string;
}

/* ==================== 客户 ==================== */

/** 客户列表项 */
export interface CustomerResp {
  id: string;
  customerName: string;
  customerCode?: string;
  address?: string;
  remark?: string;
  status?: number;
  statusLabel?: string;
  defaultContactName?: string;
  defaultContactPhone?: string;
  orderCount?: number;
  createTime?: string;
  updateTime?: string;
}

/** 客户联系人 */
export interface CustomerContactResp {
  id: string;
  customerId: string;
  contactName: string;
  title?: string;
  phone?: string;
  mobile?: string;
  email?: string;
  isDefault?: number;
  status?: number;
  remark?: string;
}

/** 客户开票信息 */
export interface CustomerInvoiceResp {
  id: string;
  customerId: string;
  invoiceTitle: string;
  taxNo?: string;
  bankName?: string;
  bankAccount?: string;
  invoiceAddress?: string;
  invoicePhone?: string;
  invoiceType?: string;
  invoiceTypeLabel?: string;
  isDefault?: number;
  status?: number;
  remark?: string;
}

/** 订单简要信息 */
export interface OrderBriefResp {
  id: string;
  orderNo: string;
  customerOrderNo?: string;
  orderDate?: string;
  status?: string;
  statusLabel?: string;
  amountTaxIncluded?: number | string;
  totalQtyOrdered?: number | string;
  totalQtyShipped?: number | string;
}

/** 客户详情 */
export interface CustomerDetailResp {
  id: string;
  customerName: string;
  customerCode?: string;
  address?: string;
  remark?: string;
  status?: number;
  contacts: CustomerContactResp[];
  invoices: CustomerInvoiceResp[];
  orders: OrderBriefResp[];
  orderCount?: number;
  totalAmount?: number | string;
}

/** 客户保存请求 */
export interface CustomerSaveReq {
  id?: string;
  customerName: string;
  customerCode?: string;
  address?: string;
  remark?: string;
  status?: number;
}

/** 联系人保存请求 */
export interface CustomerContactReq {
  id?: string;
  customerId: string;
  contactName: string;
  title?: string;
  phone?: string;
  mobile?: string;
  email?: string;
  isDefault?: number;
  status?: number;
  remark?: string;
}

/** 开票信息保存请求 */
export interface CustomerInvoiceReq {
  id?: string;
  customerId: string;
  invoiceTitle: string;
  taxNo?: string;
  bankName?: string;
  bankAccount?: string;
  invoiceAddress?: string;
  invoicePhone?: string;
  invoiceType?: string;
  isDefault?: number;
  status?: number;
  remark?: string;
}

/** 客户查询条件 */
export interface CustomerQuery {
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
  customerName?: string;
  status?: number;
}

/* ==================== 订单 ==================== */

/** 订单列表项 */
export interface OrderResp {
  id: string;
  orderNo: string;
  customerId: string;
  customerName?: string;
  customerOrderNo?: string;
  salesmanId: string;
  salesmanName?: string;
  orderDate?: string;
  expectedDeliveryDate?: string;
  status?: string;
  statusLabel?: string;
  currency?: string;
  totalQtyOrdered?: number | string;
  totalQtyShipped?: number | string;
  totalQtyOpen?: number | string;
  progress?: string;
  amountTaxIncluded?: number | string;
  remark?: string;
  createTime?: string;
}

/** 订单明细 */
export interface OrderItemResp {
  id: string;
  orderId: string;
  lineNo?: number;
  model: string;
  materialNo?: string;
  customerMaterialNo?: string;
  spec?: string;
  qtyOrdered?: number | string;
  qtyShipped?: number | string;
  qtyOpen?: number | string;
  taxRate?: number | string;
  taxRateLabel?: string;
  unitPrice?: number | string;
  priceIsTaxIncluded?: number;
  amountExTax?: number | string;
  taxAmount?: number | string;
  amountTaxIncluded?: number | string;
  reconcileDate?: string;
  reconcileStatus?: string;
  reconcileStatusLabel?: string;
  status?: string;
  statusLabel?: string;
  remark?: string;
}

/** 订单详情 */
export interface OrderDetailResp {
  id: string;
  orderNo: string;
  customerId: string;
  customerName?: string;
  customerOrderNo?: string;
  requirement?: string;
  mainboardLabel?: string;
  cartonLabel?: string;
  salesmanId: string;
  salesmanName?: string;
  orderDate?: string;
  expectedDeliveryDate?: string;
  status?: string;
  statusLabel?: string;
  currency?: string;
  settlementMethod?: string;
  deliveryMethod?: string;
  remark?: string;
  items: OrderItemResp[];
  amountTaxIncluded?: number | string;
  totalQtyOrdered?: number | string;
  totalQtyShipped?: number | string;
}

/** 明细保存请求 */
export interface OrderItemSaveReq {
  id?: string;
  lineNo?: number;
  model: string;
  materialNo?: string;
  customerMaterialNo?: string;
  spec?: string;
  qtyOrdered?: number | string;
  taxRate?: number | string;
  unitPrice?: number | string;
  reconcileDate?: string;
  remark?: string;
}

/** 订单保存请求（头 + 明细） */
export interface OrderSaveReq {
  id?: string;
  orderNo?: string;
  customerId: string;
  customerOrderNo?: string;
  requirement?: string;
  mainboardLabel?: string;
  cartonLabel?: string;
  salesmanId: string;
  orderDate?: string;
  expectedDeliveryDate?: string;
  currency?: string;
  settlementMethod?: string;
  deliveryMethod?: string;
  remark?: string;
  items: OrderItemSaveReq[];
}

/** 订单查询条件 */
export interface OrderQuery {
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
  customerId?: string;
  status?: string;
  salesmanId?: string;
  beginDate?: string;
  endDate?: string;
  onlyOpen?: boolean;
}

/* ==================== 出货 ==================== */

/** 出货记录 */
export interface ShipmentResp {
  id: string;
  shipDate?: string;
  processOrderNo?: string;
  salesmanId?: string;
  salesmanName?: string;
  customerId?: string;
  customerName?: string;
  customerOrderNo?: string;
  customerMaterialNo?: string;
  model: string;
  materialNo?: string;
  spec?: string;
  qty?: number | string;
  orderItemId?: string;
  orderNo?: string;
  amount?: number | string;
  taxAmount?: number | string;
  amountTaxIncluded?: number | string;
  status?: string;
  statusLabel?: string;
  source?: string;
  sourceLabel?: string;
  importBatchId?: string;
  remark?: string;
  createTime?: string;
}

/** 单行出货（列顺序与用户现有台账一致） */
export interface ShipmentRowReq {
  shipDate?: string;
  processOrderNo?: string;
  salesmanId?: string;
  customerId?: string;
  customerOrderNo?: string;
  customerMaterialNo?: string;
  model: string;
  qty?: number | string;
  materialNo?: string;
  spec?: string;
  remark?: string;
  orderItemId?: string;
}

/** 出货批量保存请求 */
export interface ShipmentSaveReq {
  source?: string;
  rows: ShipmentRowReq[];
}

/** 出货批量保存结果 */
export interface ShipmentBatchResultResp {
  total: number;
  successCount: number;
  failCount: number;
  results: BatchLineResult[];
}

/** 出货查询条件 */
export interface ShipmentQuery {
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
  customerId?: string;
  salesmanId?: string;
  processOrderNo?: string;
  customerOrderNo?: string;
  beginDate?: string;
  endDate?: string;
  status?: string;
  onlyOrphan?: boolean;
}

/* ==================== 导入导出 ==================== */

/** 导入预览 */
export interface ImportPreviewResp {
  batchType: string;
  fileName?: string;
  sheetName?: string;
  total: number;
  insertCount: number;
  updateCount: number;
  skipCount: number;
  failCount: number;
  warnCount: number;
  hasBlockingError: boolean;
  unknownHeaders?: string[];
  results?: BatchLineResult[];
}

/** 导入确认结果 */
export interface ImportCommitResp {
  batchId: string;
  total: number;
  insertCount: number;
  updateCount: number;
  skipCount: number;
  failCount: number;
  warnCount: number;
  results?: BatchLineResult[];
}

/** 导入批次 */
export interface ImportBatchResp {
  id: string;
  batchType?: string;
  fileName?: string;
  sheetName?: string;
  totalCount?: number;
  insertCount?: number;
  updateCount?: number;
  skipCount?: number;
  failCount?: number;
  warnCount?: number;
  idempotent?: string;
  status?: string;
  statusLabel?: string;
  createTime?: string;
}

/* ---------------- 报表与体检 ---------------- */

/** 体检问题条目 */
export interface HealthIssue {
  category: string;
  categoryLabel?: string;
  subject?: string;
  detail?: string;
}

/** 历史数据体检报告（附录A A.6） */
export interface HealthReportResp {
  customerCount: number;
  orderCount: number;
  itemCount: number;
  shipmentCount: number;
  counts?: Record<string, number>;
  issues?: HealthIssue[];
}

/** 客户出货汇总 */
export interface CustomerSummaryResp {
  customerId?: string;
  customerName?: string;
  orderCount: number;
  itemCount: number;
  qtyOrdered: number;
  qtyShipped: number;
  qtyOpen: number;
  amountTaxIncluded: number;
}

/* ---------------- 操作日志 ---------------- */

/** 操作日志 */
export interface OperationLogResp {
  id: string;
  module?: string;
  moduleLabel?: string;
  bizId?: string;
  action?: string;
  actionLabel?: string;
  beforeJson?: string;
  afterJson?: string;
  reason?: string;
  operatorId?: string;
  operatorName?: string;
  operateTime?: string;
}
