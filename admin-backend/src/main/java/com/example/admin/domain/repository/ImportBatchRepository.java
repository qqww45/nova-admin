package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.imports.ImportBatch;
import com.example.admin.domain.model.imports.ImportType;

import java.util.List;

/**
 * 导入批次仓储接口（历史批次、错误清单、一键回滚）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ImportBatchRepository {

    /**
     * 新增批次。
     *
     * @param batch 批次
     * @return 新 ID
     */
    Long save(ImportBatch batch);

    /**
     * 更新批次。
     *
     * @param batch 批次
     */
    void update(ImportBatch batch);

    /**
     * 按 ID 查询。
     *
     * @param id 批次 ID
     * @return 批次，不存在返回 null
     */
    ImportBatch findById(Long id);

    /**
     * 按模块查询历史批次。
     *
     * @param type  模块（为空表示全部）
     * @param limit 条数上限
     * @return 批次列表（按时间倒序）
     */
    List<ImportBatch> list(ImportType type, int limit);

    /**
     * 分页查询批次。
     *
     * @param type     模块
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<ImportBatch> page(ImportType type, int pageNum, int pageSize);
}
