package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.StatementGenerateReq;
import com.example.admin.interfaces.dto.req.StatementPageQuery;
import com.example.admin.interfaces.dto.req.StatementPreviewReq;
import com.example.admin.interfaces.dto.resp.ReceivableSummaryResp;
import com.example.admin.interfaces.dto.resp.StatementPreviewResp;
import com.example.admin.interfaces.dto.resp.StatementResp;

import java.util.List;

/**
 * 对账单应用服务（客户应收账款对账单）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
public interface StatementAppService {

    /** 分页查询对账单 */
    PageResult<StatementResp> page(StatementPageQuery query);

    /** 查询对账单详情（含明细） */
    StatementResp detail(String id);

    /** 实时预览聚合（不落库） */
    StatementPreviewResp preview(StatementPreviewReq req);

    /**
     * 客户应收速览：全期间期末应收余额（实时计算不落库），按余额倒序。
     *
     * <p>口径与对账单完全一致（{@code StatementGenerator} 单一实现）：
     * 期末应收 = 期初 + 累计出货 − 退货 − 累计收款。只返回有期初或有出货
     * 或有收款的客户。业务员催款前看这张表：谁欠得多、欠多久。
     *
     * @return 应收速览列表
     */
    List<ReceivableSummaryResp> receivableSummaries();

    /** 生成并保存对账单快照（草稿） */
    String generate(StatementGenerateReq req);

    /** 签发 */
    void issue(String id);

    /** 客户确认（回签无误） */
    void confirm(String id, String reason);

    /** 标记差异 */
    void markDifferent(String id, String reason);

    /** 导出对账单 Excel（传统台账格式） */
    byte[] export(String id);
}
