package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.model.shipment.ShipmentQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出货仓储接口（领域层）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ShipmentRepository {

    /**
     * 分页查询出货记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Shipment> page(ShipmentQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 出货 ID
     * @return 出货记录，不存在返回 null
     */
    Shipment findById(Long id);

    /**
     * 新增出货记录。
     *
     * @param shipment 出货记录
     * @return 新 ID
     */
    Long save(Shipment shipment);

    /**
     * 更新出货记录。
     *
     * @param shipment 出货记录
     */
    void update(Shipment shipment);

    /**
     * 幂等判重：是否已存在相同幂等键的记录。
     *
     * @param processOrderNo  加工单号
     * @param customerOrderNo 客户订单号
     * @param materialNo      料号
     * @param shipDate        出货日期
     * @param qty             数量
     * @return 已存在返回该记录，否则返回 null
     */
    Shipment findByIdempotentKey(String processOrderNo, String customerOrderNo, String materialNo,
                                 LocalDateTime shipDate, BigDecimal qty);

    /**
     * 查询某明细下的出货流水。
     *
     * @param orderItemId 明细 ID
     * @return 出货记录列表
     */
    List<Shipment> listByOrderItem(Long orderItemId);

    /**
     * 查询某导入批次下的出货记录（回滚用）。
     *
     * @param batchId 批次 ID
     * @return 出货记录列表
     */
    List<Shipment> listByBatch(Long batchId);

    /**
     * 统计今日已录条数（出货页常驻展示）。
     *
     * @param operatorId 业务员 ID（为空表示全部）
     * @return 条数
     */
    long countToday(Long operatorId);

    /**
     * 查询无源出货（异常数据池）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Shipment> pageOrphan(ShipmentQuery query);

    /**
     * 按 ID 列表批量查询（导出勾选行）。
     *
     * @param ids ID 列表
     * @return 出货记录列表
     */
    List<Shipment> listByIds(List<Long> ids);

    /**
     * 查询某客户在指定期间内的出货流水分录（对账单聚合用，已按日期升序，自动排除已作废）。
     *
     * @param customerId 客户 ID
     * @param start      期间起（含）
     * @param end        期间止（含）
     * @return 出货记录列表
     */
    List<Shipment> listByCustomerAndPeriod(Long customerId, LocalDateTime start, LocalDateTime end);

    /**
     * 全部有效出货（排除作废），应收速览等内存聚合用。
     *
     * @return 出货记录列表，按出货日期升序
     */
    List<Shipment> listAll();
}
