package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.StatementGenerateReq;
import com.example.admin.interfaces.dto.req.StatementPageQuery;
import com.example.admin.interfaces.dto.req.StatementPreviewReq;
import com.example.admin.interfaces.dto.resp.StatementPreviewResp;
import com.example.admin.interfaces.dto.resp.StatementResp;

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
