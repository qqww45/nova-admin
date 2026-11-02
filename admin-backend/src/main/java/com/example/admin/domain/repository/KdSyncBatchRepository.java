package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.kd.KdBatchQuery;
import com.example.admin.domain.model.kd.KdSyncBatch;

/**
 * 同步批次仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdSyncBatchRepository {

    /**
     * 按批次号查询。
     *
     * @param batchNo 批次号
     * @return 批次；不存在返回 null
     */
    KdSyncBatch findByBatchNo(String batchNo);

    /**
     * 新增批次。
     *
     * @param batch 批次
     * @return 新主键
     */
    Long save(KdSyncBatch batch);

    /**
     * 更新批次。
     *
     * @param batch 批次
     */
    void update(KdSyncBatch batch);

    /**
     * 分页查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<KdSyncBatch> page(KdBatchQuery query);
}
