package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.resp.OperationLogResp;

/**
 * 操作日志查询服务（M6 审计）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface LogAppService {

    /**
     * 分页查询操作日志。
     *
     * @param module   模块（为空表示全部）
     * @param action   动作（为空表示全部）
     * @param keyword  关键字：业务 ID / 原因 / 操作人
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<OperationLogResp> page(String module, String action, String keyword, int pageNum, int pageSize);

    /**
     * 查询某个业务单据的变更记录（客户详情"变更记录"页签、订单详情审计）。
     *
     * @param module 模块
     * @param bizId  业务 ID
     * @param limit  条数上限
     * @return 日志列表（按时间倒序）
     */
    java.util.List<OperationLogResp> listByBiz(String module, String bizId, int limit);
}
