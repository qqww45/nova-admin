package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdUpsertResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 金蝶出库单头仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdOutboundRepository {

    /**
     * 批量幂等写入。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param batchNo 批次号
     * @param rows 出库单头
     * @return 写入结果
     */
    KdUpsertResult upsertAll(Long ownerUserId, String acctId, String batchNo, List<KdOutbound> rows);

    /**
     * 按分录汇总回填表头金额（分录上报完成后调用）。
     *
     * <p>表头金额以分录为准：客户端上报的头金额可能为空或未税，分录才是对账口径。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param batchNo 批次号
     * @return 回填的单据数
     */
    int recalculateAmountByBatch(Long ownerUserId, String acctId, String batchNo);

    /**
     * 全量批次结束后，把本批次未见到的单据标记失效（金蝶侧物理删除兜底）。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param batchNo 本次全量批次号
     * @return 标记失效的单据数
     */
    int markInvalidNotInBatch(Long ownerUserId, String acctId, String batchNo);

    /**
     * 统计有效单据数。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @return 数量
     */
    long countByOwner(Long ownerUserId, String acctId);

    /**
     * 某用户某账套出库单的最近同步时间（绑定列表上次同步兜底）。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @return 最大 {@code sync_at}；无数据返回 null
     */
    LocalDateTime maxSyncAt(Long ownerUserId, String acctId);

    /**
     * 某用户某账套出库单的最近金蝶修改时间（绑定列表出库水位兜底）。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @return 最大 {@code kd_modify_time}；无数据返回 null
     */
    LocalDateTime maxModifyTime(Long ownerUserId, String acctId);
}
