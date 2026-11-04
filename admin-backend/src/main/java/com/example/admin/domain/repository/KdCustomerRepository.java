package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdUpsertResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 金蝶客户仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdCustomerRepository {

    /**
     * 批量幂等写入（按 owner + acct + kdFid 先查后写）。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param batchNo 批次号
     * @param rows 客户列表
     * @return 写入结果
     */
    KdUpsertResult upsertAll(Long ownerUserId, String acctId, String batchNo, List<KdCustomer> rows);

    /**
     * 查询某用户某账套的客户数。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @return 数量
     */
    long countByOwner(Long ownerUserId, String acctId);

    /**
     * 某用户某账套客户行的最近同步时间（绑定列表水位 / 上次同步兜底）。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @return 最大 {@code sync_at}；无数据返回 null
     */
    LocalDateTime maxSyncAt(Long ownerUserId, String acctId);
}
