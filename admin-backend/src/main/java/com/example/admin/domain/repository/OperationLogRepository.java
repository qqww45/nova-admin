package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.log.OperationLogQuery;

import java.util.List;

/**
 * 业务操作日志仓储接口（出货 / 导入 / 回滚 / 作废 / 改价 / 对账 必须留痕）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface OperationLogRepository {

    /**
     * 写入一条操作日志。
     *
     * @param log 操作日志
     */
    void save(OperationLog log);

    /**
     * 按业务单据查询变更记录。
     *
     * @param module 模块
     * @param bizId  业务 ID
     * @param limit  条数上限
     * @return 操作日志列表（按时间倒序）
     */
    List<OperationLog> listByBiz(String module, String bizId, int limit);

    /**
     * 分页查询操作日志（审计页面）。
     *
     * @param query 查询条件
     * @return 分页结果（按操作时间倒序）
     */
    PageResult<OperationLog> page(OperationLogQuery query);
}
