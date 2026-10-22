package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.reconciliation.Statement;
import com.example.admin.domain.model.reconciliation.StatementLine;
import com.example.admin.domain.model.reconciliation.StatementQuery;

import java.util.List;

/**
 * 对账单仓储接口（领域层）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
public interface StatementRepository {

    /** 分页查询 */
    PageResult<Statement> page(StatementQuery query);

    /** 按 ID 查询（不含明细，明细用 {@link #findLines(Long)}） */
    Statement findById(Long id);

    /** 查询对账单明细 */
    List<StatementLine> findLines(Long statementId);

    /** 新增对账单头，返回主键 */
    Long save(Statement statement);

    /** 更新对账单头 */
    void update(Statement statement);

    /** 新增明细行 */
    void saveLine(StatementLine line);

    /** 删除某对账单的全部明细（重新生成前清空用） */
    void deleteLines(Long statementId);
}
