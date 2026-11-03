package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdCustomerBrowseQuery;
import com.example.admin.domain.model.kd.KdLineBrowseQuery;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundBrowseQuery;
import com.example.admin.domain.model.kd.KdOutboundLine;

import java.util.List;
import java.util.Map;

/**
 * 金蝶同步数据浏览仓储（只读，供排查页面取数）。
 *
 * <p>为什么不复用 {@link KdStatementRepository}：对账单有自己的口径
 * （排除作废 / 失效单），而排查页面恰恰要看到<strong>全部</strong>行——
 * 包括作废单、失效单、没有分录的单，混用口径会让"对账单少一张单"
 * 这类问题在浏览页里被过滤掉而查不出来。
 *
 * <p>为什么不复用 {@link KdOutboundRepository} 等写侧接口：那些接口服务
 * 采集器上报（upsert / 差集标记 / 金额重算），往里加浏览查询会让写侧
 * 接口随页面需求无限膨胀，也容易被误用于写入场景。
 *
 * <p>隔离口径由调用方（应用层）决定：{@code ownerUserId} 为 null 表示
 * 跨归属人查询，只允许管理员这样传。
 *
 * @author admin-system
 * @since 2026-11-03
 */
public interface KdDataRepository {

    /**
     * 分页查客户（含禁用行）。
     *
     * @param query 查询条件
     * @return 分页结果，按最近同步时间倒序
     */
    PageResult<KdCustomer> pageCustomers(KdCustomerBrowseQuery query);

    /**
     * 分页查出库单头（含作废 / 失效行）。
     *
     * @param query 查询条件
     * @return 分页结果，按业务日期倒序
     */
    PageResult<KdOutbound> pageOutbounds(KdOutboundBrowseQuery query);

    /**
     * 分页查出库分录（含失效行）。
     *
     * @param query 查询条件
     * @return 分页结果，按最近同步时间倒序
     */
    PageResult<KdOutboundLine> pageLines(KdLineBrowseQuery query);

    /**
     * 按主键取出库单头（不过滤任何状态）。
     *
     * @param id 主键
     * @return 单据；不存在返回 null
     */
    KdOutbound findBillById(Long id);

    /**
     * 取一张单的全部分录（含失效行，排查页不做状态过滤）。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @param acctId 账套
     * @param outboundFid 金蝶出库单 FID
     * @return 分录，按分录内码升序
     */
    List<KdOutboundLine> listLinesOfBill(Long ownerUserId, String acctId, Long outboundFid);

    /**
     * 按账套 + FID 批量取出库单头，用于分录页回填单据编号。
     *
     * <p>入参按账套分组而不是裸 FID 集合：金蝶 FID 只在账套内唯一，
     * 跨账套只按 FID 关联会把 A 账套的单号挂到 B 账套的分录上。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @param fidsByAcct 账套 → 金蝶 FID 列表
     * @return 单据头列表
     */
    List<KdOutbound> findBillsByFids(Long ownerUserId, Map<String, List<Long>> fidsByAcct);

    /**
     * 统计一批单据的分录行数（原始行数，不排除失效行）。
     *
     * <p>用途：出库列表展示"该单有无分录"——缺分录的单据在对账单里
     * 会退回表头金额（docs/金蝶对账同步方案.md §12.2 第 5 条），
     * 行数是排查金额对不上的第一线索。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @param fidsByAcct 账套 → 金蝶 FID 列表
     * @return key = "账套#FID"，value = 分录行数
     */
    Map<String, Integer> countLinesByBill(Long ownerUserId, Map<String, List<Long>> fidsByAcct);
}
