package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.order.OrderQuery;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单仓储接口（领域层）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface OrderRepository {

    /**
     * 分页查询订单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesOrder> page(OrderQuery query);

    /**
     * 按 ID 查询订单（含明细）。
     *
     * @param id 订单 ID
     * @return 订单，不存在返回 null
     */
    SalesOrder findById(Long id);

    /**
     * 按订单号查询。
     *
     * @param orderNo 订单号
     * @return 订单，不存在返回 null
     */
    SalesOrder findByOrderNo(String orderNo);

    /**
     * 订单号是否已存在。
     *
     * @param orderNo   订单号
     * @param excludeId 排除的订单 ID，可为 null
     * @return true 表示已存在
     */
    boolean existsByOrderNo(String orderNo, Long excludeId);

    /**
     * 新增订单头。
     *
     * @param order 订单
     * @return 新 ID
     */
    Long save(SalesOrder order);

    /**
     * 更新订单头（带乐观锁）。
     *
     * @param order 订单
     */
    void update(SalesOrder order);

    /**
     * 逻辑删除订单及其明细。
     *
     * @param id 订单 ID
     */
    void remove(Long id);

    /**
     * 查询订单下的全部明细。
     *
     * @param orderId 订单 ID
     * @return 明细列表（按行号排序）
     */
    List<SalesOrderItem> listItems(Long orderId);

    /**
     * 按 ID 查询明细。
     *
     * @param id 明细 ID
     * @return 明细，不存在返回 null
     */
    SalesOrderItem findItem(Long id);

    /**
     * 新增明细。
     *
     * @param item 明细
     * @return 新 ID
     */
    Long saveItem(SalesOrderItem item);

    /**
     * 更新明细（带乐观锁）。
     *
     * @param item 明细
     */
    void updateItem(SalesOrderItem item);

    /**
     * 逻辑删除明细。
     *
     * @param id 明细 ID
     */
    void removeItem(Long id);

    /**
     * 按客户查询订单（客户详情"关联订单"页签）。
     *
     * @param customerId 客户 ID
     * @param limit      条数上限
     * @return 订单列表
     */
    List<SalesOrder> listByCustomer(Long customerId, int limit);

    /**
     * 按客户订单号查询订单（出货匹配用）。
     *
     * @param customerOrderNo 客户订单号
     * @return 订单，不存在返回 null
     */
    SalesOrder findByCustomerOrderNo(String customerOrderNo);

    /**
     * 出货匹配：按客户订单号 + 客户物料号查候选明细（附录A A.4 优先级一）。
     *
     * @param customerOrderNo    客户订单号
     * @param customerMaterialNo 客户物料号
     * @return 候选明细
     */
    List<SalesOrderItem> matchByCustomerOrderAndMaterial(String customerOrderNo, String customerMaterialNo);

    /**
     * 出货匹配：按客户 + 料号查候选明细（优先级三）。
     *
     * @param customerId  客户 ID
     * @param materialNo  料号
     * @return 候选明细
     */
    List<SalesOrderItem> matchByCustomerAndMaterial(Long customerId, String materialNo);

    /**
     * 按下单时间范围查询订单（含明细），供报表与体检报告使用。
     *
     * <p>为什么单独给一个方法而不是复用 {@link #page}：报表要的是"区间内全量"，
     * 用分页接口去翻页既慢又容易漏；但也正因为是全量，调用方必须传时间区间，
     * 不允许无界扫描（实现里再做一次上限保护）。
     *
     * @param begin 起始时间（含），为空表示不限
     * @param end   结束时间（含），为空表示不限
     * @return 订单列表（含明细）
     */
    List<SalesOrder> listForReport(LocalDateTime begin, LocalDateTime end);
}
