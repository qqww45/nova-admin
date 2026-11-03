package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.KdStatementQuery;
import com.example.admin.interfaces.dto.resp.KdStatementDetailResp;
import com.example.admin.interfaces.dto.resp.KdStatementHealthResp;
import com.example.admin.interfaces.dto.resp.KdStatementSummaryResp;

/**
 * 对账单应用服务（基于采集器上报的 kd_* 数据）。
 *
 * <p>与 {@code StatementAppService} 的区别：那一套基于本系统自己的出货/收款单，
 * 有期初、收款、确认流转；这一套只回答"金蝶里这个客户这段时间出了多少货"，
 * 供业务员与客户对账时核对，不参与应收账龄计算，因此不落库、不做确认流。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdStatementAppService {

    /**
     * 按客户汇总。
     *
     * @param query 查询条件
     * @return 分页汇总
     */
    PageResult<KdStatementSummaryResp> summary(KdStatementQuery query);

    /**
     * 单客户明细。
     *
     * @param query 查询条件（customerFid 必填）
     * @return 明细
     */
    KdStatementDetailResp detail(KdStatementQuery query);

    /**
     * 导出 xlsx（汇总 + 明细两个工作表）。
     *
     * @param query 查询条件
     * @return 文件字节
     */
    byte[] export(KdStatementQuery query);

    /**
     * 数据体检：解释"本期为什么对账单少数据 / 金额对不上"。
     *
     * <p>统计窗口内三类不进对账单的单据：无分录（退回表头金额）、作废、失效。
     * 口径与 {@link #summary} 相同的隔离与窗口限制。
     *
     * @param query 查询条件
     * @return 体检结果
     */
    KdStatementHealthResp health(KdStatementQuery query);
}
