import http from '@/utils/request';
import type { PageResult } from '@/types/api';
import type {
  KdBatchQuery,
  KdBindingCodeResp,
  KdBindingRedeemResp,
  KdBindingResp,
  KdCommandResp,
  KdCustomerDataQuery,
  KdCustomerDataResp,
  KdFieldMappingResp,
  KdFieldMappingSaveReq,
  KdLineDataQuery,
  KdOutboundDataQuery,
  KdOutboundDataResp,
  KdOutboundLineDataResp,
  KdOwnerResp,
  KdStatementDetailResp,
  KdStatementQuery,
  KdStatementSummaryResp,
  KdSyncBatchResp,
  KdSyncTaskResp,
  KdTaskSaveReq,
} from '@/types/kd';

/**
 * 金蝶同步接口（绑定 / 批次 / 配置）。
 *
 * <p>采集器侧的上报接口（/kd/sync/customer 等）用 X-Kd-Token，
 * 由桌面端直接调用，前端不涉及。
 */

export const kdBindingApi = {
  /** 绑定列表（非管理员只看自己） */
  list(): Promise<KdBindingResp[]> {
    return http.get<KdBindingResp[]>('/kd/binding/list');
  },

  /** 生成一次性绑定码（5 分钟有效） */
  createCode(remark?: string): Promise<KdBindingCodeResp> {
    return http.post<KdBindingCodeResp>('/kd/binding/code', { remark });
  },

  /** 吊销令牌 */
  revoke(id: string): Promise<void> {
    return http.put<void>(`/kd/binding/${id}/revoke`);
  },

  /** 轮换令牌（水位与历史数据保留） */
  refresh(id: string): Promise<KdBindingRedeemResp> {
    return http.post<KdBindingRedeemResp>(`/kd/binding/${id}/refresh`);
  },

  /** 下发指令（sync.now / config.reload / token.revoke / client.update） */
  sendCommand(id: string, command: string, payload?: string): Promise<KdCommandResp> {
    return http.post<KdCommandResp>(`/kd/binding/${id}/command`, { command, payload });
  },

  /** 最近指令（含回执） */
  commands(id: string): Promise<KdCommandResp[]> {
    return http.get<KdCommandResp[]>(`/kd/binding/${id}/commands`);
  },
};

export const kdSyncApi = {
  /** 同步批次分页 */
  batches(params: KdBatchQuery): Promise<PageResult<KdSyncBatchResp>> {
    return http.get<PageResult<KdSyncBatchResp>>('/kd/sync/batch/list', params);
  },
};

export const kdConfigApi = {
  /** 任务清单 */
  tasks(): Promise<KdSyncTaskResp[]> {
    return http.get<KdSyncTaskResp[]>('/kd/config/task/list');
  },

  /** 保存任务（按账套 + 实体唯一） */
  saveTask(data: KdTaskSaveReq): Promise<string> {
    return http.post<string>('/kd/config/task', data);
  },

  /** 某绑定的字段映射 */
  mappings(bindingId: string): Promise<KdFieldMappingResp[]> {
    return http.get<KdFieldMappingResp[]>('/kd/config/mapping', { bindingId });
  },

  /** 保存字段映射 */
  saveMapping(data: KdFieldMappingSaveReq): Promise<void> {
    return http.post<void>('/kd/config/mapping', data);
  },
};

/**
 * 金蝶同步数据查看（排查页）。
 *
 * <p>只读接口，业务员只见本人数据（服务层强制 ownerUserId 隔离）；
 * 管理员可跨业务员并显式按业务员过滤。
 */
export const kdDataApi = {
  /** 客户数据分页 */
  customers(params: KdCustomerDataQuery): Promise<PageResult<KdCustomerDataResp>> {
    return http.get<PageResult<KdCustomerDataResp>>('/kd/data/customer', params);
  },

  /** 出库单数据分页（含作废 / 失效行） */
  outbounds(params: KdOutboundDataQuery): Promise<PageResult<KdOutboundDataResp>> {
    return http.get<PageResult<KdOutboundDataResp>>('/kd/data/outbound', params);
  },

  /** 一张出库单的全部分录（列表展开行用，含失效行） */
  outboundLines(id: string): Promise<KdOutboundLineDataResp[]> {
    return http.get<KdOutboundLineDataResp[]>(`/kd/data/outbound/${id}/lines`);
  },

  /** 出库分录跨单检索（物料 / 源单号 / 客户PO / 批次） */
  lines(params: KdLineDataQuery): Promise<PageResult<KdOutboundLineDataResp>> {
    return http.get<PageResult<KdOutboundLineDataResp>>('/kd/data/line', params);
  },

  /** 有采集器绑定的业务员（管理员筛选下拉用） */
  owners(): Promise<KdOwnerResp[]> {
    return http.get<KdOwnerResp[]>('/kd/data/owners');
  },
};

/**
 * 触发浏览器下载（与 api/biz.ts 的写法保持一致）。
 *
 * @param data 文件内容
 * @param fileName 文件名
 */
function downloadBlob(data: Blob, fileName: string): void {
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

export const kdStatementApi = {
  /** 按客户汇总 */
  summary(params: KdStatementQuery): Promise<PageResult<KdStatementSummaryResp>> {
    return http.get<PageResult<KdStatementSummaryResp>>('/kd/statement/summary', params);
  },

  /** 单客户明细 */
  detail(params: KdStatementQuery): Promise<KdStatementDetailResp> {
    return http.get<KdStatementDetailResp>('/kd/statement/detail', params);
  },

  /** 导出 xlsx（客户汇总 + 出库明细） */
  async export(params: KdStatementQuery): Promise<void> {
    const response = await http.get<Blob>('/kd/statement/export', params, {
      responseType: 'blob',
      // 全局 20s 超时按"接口响应"设计；导出要服务端算完全部分录再传整份文件，
      // 慢链路（服务器在国际链路后）下载几 MB 的 xlsx 很容易超过 20s，
      // 一旦超时前端只弹"网络连接异常"且拿不到文件。这里放宽到 120s。
      timeout: 120_000,
    } as never);
    const range = `${params.dateFrom || '起'}_${params.dateTo || '今'}`;
    downloadBlob(response, `金蝶对账单-${range}.xlsx`);
  },
};
