package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.model.kd.KdUpsertResult;

import java.util.List;

/**
 * 金蝶出库单分录仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdOutboundLineRepository {

    /**
     * 批量幂等写入（按 owner + acct + entryId）。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param batchNo 批次号
     * @param rows 分录列表
     * @return 写入结果
     */
    KdUpsertResult upsertAll(Long ownerUserId, String acctId, String batchNo, List<KdOutboundLine> rows);

    /**
     * 全量批次结束后，把本批次未见到的分录标记失效。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param batchNo 本次全量批次号
     * @return 标记失效的行数
     */
    int markInvalidNotInBatch(Long ownerUserId, String acctId, String batchNo);

    /**
     * 统计有效分录数。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @return 数量
     */
    long countByOwner(Long ownerUserId, String acctId);
}
