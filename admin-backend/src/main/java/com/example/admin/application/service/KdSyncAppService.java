package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.KdBatchPageQuery;
import com.example.admin.interfaces.dto.req.KdCustomerSyncReq;
import com.example.admin.interfaces.dto.req.KdOutboundLineSyncReq;
import com.example.admin.interfaces.dto.req.KdOutboundSyncReq;
import com.example.admin.interfaces.dto.req.KdSyncBatchFinishReq;
import com.example.admin.interfaces.dto.req.KdSyncBatchStartReq;
import com.example.admin.interfaces.dto.resp.KdClientTaskResp;
import com.example.admin.interfaces.dto.resp.KdSyncBatchResp;
import com.example.admin.interfaces.dto.resp.KdSyncResultResp;

import java.util.List;

/**
 * 采集器上行同步应用服务。
 *
 * <p>核心不变量：
 * <ul>
 *     <li>归属人与账套只从令牌里取，绝不读请求体；</li>
 *     <li>幂等 upsert（先查后写），客户端重跑同一批次不会产生重复行；</li>
 *     <li>水位只进不退，且仅在服务端确认落库后推进；</li>
 *     <li>全量批次结束时按批次差集标记失效，不做物理删除。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdSyncAppService {

    /**
     * 下发当前采集器的任务清单（含账套字段映射与水位）。
     *
     * @return 任务列表
     */
    List<KdClientTaskResp> clientTasks();

    /**
     * 开启同步批次（同批次号重复调用是幂等的）。
     *
     * @param req 请求
     */
    void startBatch(KdSyncBatchStartReq req);

    /**
     * 上报客户。
     *
     * @param req 请求
     * @return 结果
     */
    KdSyncResultResp uploadCustomers(KdCustomerSyncReq req);

    /**
     * 上报出库单头。
     *
     * @param req 请求
     * @return 结果
     */
    KdSyncResultResp uploadOutbounds(KdOutboundSyncReq req);

    /**
     * 上报出库分录（完成后重算表头金额）。
     *
     * @param req 请求
     * @return 结果
     */
    KdSyncResultResp uploadLines(KdOutboundLineSyncReq req);

    /**
     * 批次结束回报。
     *
     * @param batchNo 批次号
     * @param req 请求
     */
    void finishBatch(String batchNo, KdSyncBatchFinishReq req);

    /**
     * 分页查询批次（非管理员只看自己的）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<KdSyncBatchResp> batchPage(KdBatchPageQuery query);
}
