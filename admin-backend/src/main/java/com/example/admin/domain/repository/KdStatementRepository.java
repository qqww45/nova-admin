package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账取数仓储（只读）。
 *
 * <p>为什么不复用 {@link KdOutboundRepository}：那个接口是"写侧"（上报落库、作废、
 * 批次回填）。对账单查询有自己的过滤口径（排除作废 / 失效、按客户 + 期间），
 * 混在一起会让写侧每加一个业务查询就膨胀一次，也容易被人误用于写入。
 *
 * <p>隔离口径由调用方（应用层）决定：{@code ownerUserId} 为 null 表示跨归属人查询，
 * 只允许管理员这样传。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdStatementRepository {

    /**
     * 查有效出库单头。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @param acctId 账套；空表示不限
     * @param from 业务日期起（含）；null 不限
     * @param to 业务日期止（含）；null 不限
     * @param customerFid 客户内码；null 不限
     * @return 单据列表，按业务日期升序
     */
    List<KdOutbound> listBills(Long ownerUserId, String acctId, LocalDateTime from, LocalDateTime to,
                               Long customerFid);

    /**
     * 按单据批量查有效分录。
     *
     * <p>入参是账单而不是 FID 集合：金蝶 FID 只在账套内唯一，不同账套完全可能都有
     * {@code fid=90001}。只按 FID 关联会把 A 账套的分录挂到 B 账套的单上
     * （表现为"一张单 4 行、金额翻倍"），因此必须带上 acctId 分组过滤。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @param bills 单据列表（取其 acctId + fid）
     * @return 分录列表（按单据 + 分录内码排序）
     */
    List<KdOutboundLine> listLines(Long ownerUserId, List<KdOutbound> bills);

    /**
     * 查窗口内全部出库单头（不过滤作废 / 失效），体检用。
     *
     * <p>作废与失效本身就是"对账单少数据"的常见根因（方案 §3.3），
     * 体检口径与对账口径相反：必须看得到被对账单排除的行才能解释差额。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @param acctId 账套；空表示不限
     * @param from 业务日期起（含）；null 不限
     * @param to 业务日期止（含）；null 不限
     * @return 单据列表，按业务日期升序
     */
    List<KdOutbound> listAllBillsIncludingExcluded(Long ownerUserId, String acctId,
                                                   LocalDateTime from, LocalDateTime to);
}
