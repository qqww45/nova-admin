import http from '@/utils/request';
import type {
  CustomerContactReq,
  CustomerContactResp,
  CustomerDetailResp,
  CustomerInvoiceReq,
  CustomerQuery,
  CustomerResp,
  CustomerSaveReq,
  DictResp,
  ImportBatchResp,
  ImportCommitResp,
  ImportPreviewResp,
  OrderDetailResp,
  OrderItemResp,
  OrderQuery,
  OrderResp,
  OrderSaveReq,
  OperationLogResp,
  CustomerSummaryResp,
  HealthReportResp,
  ShipmentBatchResultResp,
  ShipmentQuery,
  ShipmentResp,
  ShipmentSaveReq,
  ReceiptResp,
  ReceiptQuery,
  ReceiptSaveReq,
  CustomerOpeningResp,
  CustomerOpeningSaveReq,
  StatementResp,
  StatementQuery,
  StatementPreviewReq,
  StatementPreviewResp,
  StatementGenerateReq,
} from '@/types/biz';
import type { PageResult } from '@/types/api';

/**
 * 客户 / 订单 / 出货 / 导入导出 / 字典 接口。
 *
 * <p>接口路径与后端 BizXxxController 一一对应；
 * 这里只做"传参 + 拆包"（拆包已在 request.ts 完成），不在此处做业务判断。
 */

/* ---------------- 客户 ---------------- */

export const customerApi = {
  /** 客户分页 */
  page(params: CustomerQuery): Promise<PageResult<CustomerResp>> {
    return http.get<PageResult<CustomerResp>>('/biz/customer/list', params);
  },

  /** 客户详情（含联系人、开票信息、关联订单） */
  detail(id: string): Promise<CustomerDetailResp> {
    return http.get<CustomerDetailResp>(`/biz/customer/${id}`);
  },

  /** 客户下拉搜索 */
  search(keyword: string, limit = 20): Promise<CustomerResp[]> {
    return http.get<CustomerResp[]>('/biz/customer/search', { keyword, limit });
  },

  create(data: CustomerSaveReq): Promise<string> {
    return http.post<string>('/biz/customer', data);
  },

  update(data: CustomerSaveReq): Promise<void> {
    return http.put<void>('/biz/customer', data);
  },

  remove(ids: string[]): Promise<void> {
    return http.delete<void>(`/biz/customer/${ids.join(',')}`);
  },

  saveContact(data: CustomerContactReq): Promise<string> {
    return http.post<string>('/biz/customer/contact', data);
  },

  removeContact(id: string): Promise<void> {
    return http.delete<void>(`/biz/customer/contact/${id}`);
  },

  setDefaultContact(id: string): Promise<void> {
    return http.put<void>(`/biz/customer/contact/${id}/default`);
  },

  saveInvoice(data: CustomerInvoiceReq): Promise<string> {
    return http.post<string>('/biz/customer/invoice', data);
  },

  removeInvoice(id: string): Promise<void> {
    return http.delete<void>(`/biz/customer/invoice/${id}`);
  },

  setDefaultInvoice(id: string): Promise<void> {
    return http.put<void>(`/biz/customer/invoice/${id}/default`);
  },
};

/* ---------------- 订单 ---------------- */

export const orderApi = {
  page(params: OrderQuery): Promise<PageResult<OrderResp>> {
    return http.get<PageResult<OrderResp>>('/biz/order/list', params);
  },

  detail(id: string): Promise<OrderDetailResp> {
    return http.get<OrderDetailResp>(`/biz/order/${id}`);
  },

  generateOrderNo(): Promise<string> {
    return http.get<string>('/biz/order/order-no');
  },

  create(data: OrderSaveReq): Promise<string> {
    return http.post<string>('/biz/order', data);
  },

  update(data: OrderSaveReq): Promise<void> {
    return http.put<void>('/biz/order', data);
  },

  remove(ids: string[]): Promise<void> {
    return http.delete<void>(`/biz/order/${ids.join(',')}`);
  },

  confirm(id: string): Promise<void> {
    return http.put<void>(`/biz/order/${id}/confirm`);
  },

  cancel(id: string, reason: string): Promise<void> {
    return http.put<void>(`/biz/order/${id}/cancel`, { reason });
  },

  reconcile(itemIds: string[], date?: string): Promise<void> {
    return http.put<void>('/biz/order/reconcile', { itemIds: itemIds.join(','), date });
  },
};

/* ---------------- 出货 ---------------- */

export const shipmentApi = {
  page(params: ShipmentQuery): Promise<PageResult<ShipmentResp>> {
    return http.get<PageResult<ShipmentResp>>('/biz/shipment/list', params);
  },

  detail(id: string): Promise<ShipmentResp> {
    return http.get<ShipmentResp>(`/biz/shipment/${id}`);
  },

  /** 批量保存（界面录入 / 粘贴 / 导入共用，返回逐行结果） */
  create(data: ShipmentSaveReq): Promise<ShipmentBatchResultResp> {
    return http.post<ShipmentBatchResultResp>('/biz/shipment', data);
  },

  voidShipment(id: string, reason: string): Promise<void> {
    return http.put<void>(`/biz/shipment/${id}/void`, { reason });
  },

  listByItem(itemId: string): Promise<ShipmentResp[]> {
    return http.get<ShipmentResp[]>(`/biz/shipment/by-item/${itemId}`);
  },

  /** 出货自动带出：按 A.4 三级优先级反查候选明细 */
  matchItems(params: {
    customerOrderNo?: string;
    customerMaterialNo?: string;
    model?: string;
    materialNo?: string;
    customerId?: string;
  }): Promise<OrderItemResp[]> {
    return http.post<OrderItemResp[]>('/biz/order/items/match', params);
  },

  /** 无源出货补挂订单明细（异常数据池修复） */
  linkToItem(id: string, orderItemId: string): Promise<void> {
    return http.put<void>(`/biz/shipment/${id}/link`, { orderItemId });
  },

  todayCount(): Promise<number> {
    return http.get<number>('/biz/shipment/today-count');
  },
};

/* ---------------- 导入导出 ---------------- */

/** 下载文件的通用处理：后端返回二进制流，前端造 Blob 触发下载 */
function downloadBlob(data: Blob, fileName: string): void {
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

export const importExportApi = {
  /** 第一步：上传解析（只校验不落库） */
  preview(
    type: string,
    file: File,
    sheetName?: string,
    idempotent = 'SKIP',
  ): Promise<ImportPreviewResp> {
    const form = new FormData();
    form.append('type', type);
    form.append('file', file);
    if (sheetName) {
      form.append('sheetName', sheetName);
    }
    form.append('idempotent', idempotent);
    return http.post<ImportPreviewResp>('/biz/import/preview', form);
  },

  /** 第三步：确认导入 */
  confirm(
    type: string,
    file: File,
    sheetName?: string,
    idempotent = 'SKIP',
  ): Promise<ImportCommitResp> {
    const form = new FormData();
    form.append('type', type);
    form.append('file', file);
    if (sheetName) {
      form.append('sheetName', sheetName);
    }
    form.append('idempotent', idempotent);
    return http.post<ImportCommitResp>('/biz/import/confirm', form);
  },

  /** 下载导入模板 */
  async template(type: string, template: 'SYSTEM' | 'LEGACY' = 'SYSTEM'): Promise<void> {
    const response = await http.get<Blob>('/biz/import/template', { type, template }, {
      responseType: 'blob',
    } as never);
    downloadBlob(response, `${type}-导入模板-${template === 'LEGACY' ? '传统台账' : '系统模板'}.xlsx`);
  },

  /**
   * 导出数据。
   *
   * <p>日期区间只对出货生效（订单导出按订单状态查，不按时间），
   * 报表页的"出货明细账"就是靠这两个参数把区间传给服务端。
   */
  async export(
    type: string,
    template: 'SYSTEM' | 'LEGACY' = 'SYSTEM',
    keyword?: string,
    beginDate?: string,
    endDate?: string,
  ): Promise<void> {
    const response = await http.get<Blob>(
      '/biz/import/export',
      { type, template, keyword, beginDate, endDate },
      { responseType: 'blob' } as never,
    );
    downloadBlob(response, `${type}-导出.xlsx`);
  },

  /** 历史批次 */
  batches(type?: string, limit = 20): Promise<ImportBatchResp[]> {
    return http.get<ImportBatchResp[]>('/biz/import/batches', { type, limit });
  },

  /** 回滚批次 */
  rollback(batchId: string, reason: string): Promise<void> {
    return http.put<void>(`/biz/import/${batchId}/rollback`, { reason });
  },

  /** 下载《错误清单 xlsx》 */
  async errorReport(batchId: string): Promise<void> {
    const response = await http.get<Blob>(
      `/biz/import/${batchId}/errors`,
      {},
      { responseType: 'blob' } as never,
    );
    downloadBlob(response, `导入错误清单-${batchId}.xlsx`);
  },
};

/* ---------------- 报表与体检 ---------------- */

export const reportApi = {
  /** 历史数据体检报告 */
  health(): Promise<HealthReportResp> {
    return http.get<HealthReportResp>('/biz/report/health');
  },

  /** 客户出货汇总 */
  customerSummary(begin?: string, end?: string): Promise<CustomerSummaryResp[]> {
    return http.get<CustomerSummaryResp[]>('/biz/report/customer-summary', { begin, end });
  },
};

/* ---------------- 操作日志 ---------------- */

export const logApi = {
  page(params: {
    module?: string;
    action?: string;
    keyword?: string;
    pageNum: number;
    pageSize: number;
  }): Promise<PageResult<OperationLogResp>> {
    return http.get<PageResult<OperationLogResp>>('/biz/log/list', params);
  },

  listByBiz(module: string, bizId: string, limit = 20): Promise<OperationLogResp[]> {
    return http.get<OperationLogResp[]>(`/biz/log/biz/${module}/${bizId}`, { limit });
  },
};

/* ---------------- 字典 ---------------- */

export const dictApi = {
  /** 一次性拉全量字典，前端缓存（避免每个下拉都发一次请求） */
  all(): Promise<DictResp[]> {
    return http.get<DictResp[]>('/biz/dict/all');
  },

  byCode(dictCode: string): Promise<DictResp[]> {
    return http.get<DictResp[]>(`/biz/dict/${dictCode}`);
  },
};

/* ---------------- 收款登记 / 期初应收 ---------------- */

export const receiptApi = {
  /** 分页查询收款记录 */
  page(params: ReceiptQuery): Promise<PageResult<ReceiptResp>> {
    return http.get<PageResult<ReceiptResp>>('/biz/receipt/list', params);
  },

  /** 收款详情 */
  detail(id: string): Promise<ReceiptResp> {
    return http.get<ReceiptResp>(`/biz/receipt/${id}`);
  },

  /** 新增/更新收款登记 */
  save(data: ReceiptSaveReq): Promise<string> {
    return http.post<string>('/biz/receipt', data);
  },

  /** 作废收款 */
  voidReceipt(id: string, reason: string): Promise<void> {
    return http.put<void>(`/biz/receipt/${id}/void`, { reason });
  },

  /** 查询客户期初应收 */
  opening(customerId: string): Promise<CustomerOpeningResp> {
    return http.get<CustomerOpeningResp>('/biz/receipt/opening', { customerId });
  },

  /** 保存客户期初应收 */
  saveOpening(data: CustomerOpeningSaveReq): Promise<void> {
    return http.put<void>('/biz/receipt/opening', data);
  },
};

/* ---------------- 对账单 ---------------- */

export const statementApi = {
  /** 分页查询对账单 */
  page(params: StatementQuery): Promise<PageResult<StatementResp>> {
    return http.get<PageResult<StatementResp>>('/biz/statement/list', params);
  },

  /** 对账单详情（含明细） */
  detail(id: string): Promise<StatementResp> {
    return http.get<StatementResp>(`/biz/statement/${id}`);
  },

  /** 实时预览（不落库） */
  preview(data: StatementPreviewReq): Promise<StatementPreviewResp> {
    return http.post<StatementPreviewResp>('/biz/statement/preview', data);
  },

  /** 生成并保存对账单快照 */
  generate(data: StatementGenerateReq): Promise<string> {
    return http.post<string>('/biz/statement', data);
  },

  /** 签发 */
  issue(id: string): Promise<void> {
    return http.put<void>(`/biz/statement/${id}/issue`);
  },

  /** 客户确认 */
  confirm(id: string, reason: string): Promise<void> {
    return http.put<void>(`/biz/statement/${id}/confirm`, { reason });
  },

  /** 标记差异 */
  markDifferent(id: string, reason: string): Promise<void> {
    return http.put<void>(`/biz/statement/${id}/different`, { reason });
  },

  /** 导出 Excel（传统台账格式） */
  async export(id: string): Promise<void> {
    const response = await http.get<Blob>(
      '/biz/statement/export',
      { id },
      { responseType: 'blob' } as never,
    );
    downloadBlob(response, `对账单-${id}.xlsx`);
  },
};
