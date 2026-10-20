package com.example.admin.domain.service;

import com.example.admin.domain.model.order.ItemStatus;
import com.example.admin.domain.model.order.OrderStatus;
import com.example.admin.domain.model.order.SalesOrderItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单状态自动流转规则（需求 4.3）。
 *
 * <p>规则：
 * <ul>
 *     <li>存在明细 {@code 0 < qty_shipped < qty_ordered} → 部分出货；</li>
 *     <li>所有<b>有效</b>明细（status=NORMAL）的 {@code qty_open = 0} → 已完成；</li>
 *     <li>一条都没出 → 维持草稿 / 已确认（本类不擅自把草稿升为已确认，确认是人工动作）；</li>
 *     <li>已取消是终态，不再参与自动流转。</li>
 * </ul>
 *
 * <p>取舍：状态一律由服务端在出货 / 作废后调用本类重算，前端只展示不修改，
 * 避免出现"界面显示已完成但明细还有未出货"的口径分裂。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class OrderStatusPolicy {

    private OrderStatusPolicy() {
    }

    /**
     * 按明细重算订单状态。
     *
     * @param current 当前订单状态
     * @param items   订单下的全部明细（含已取消行）
     * @return 重算后的状态；当前为已取消时原样返回
     */
    public static OrderStatus recalculate(OrderStatus current, List<SalesOrderItem> items) {
        if (current == OrderStatus.CANCELED) {
            return OrderStatus.CANCELED;
        }
        if (items == null || items.isEmpty()) {
            // 无明细：不做流转，保持原状（草稿或已确认）
            return current == null ? OrderStatus.DRAFT : current;
        }
        List<SalesOrderItem> valid = items.stream()
                .filter(i -> i != null && i.getStatus() != ItemStatus.CANCELED)
                .toList();
        if (valid.isEmpty()) {
            // 全部明细已取消
            return OrderStatus.CANCELED;
        }
        boolean anyShipped = false;
        boolean allDone = true;
        for (SalesOrderItem item : valid) {
            BigDecimal shipped = AmountCalculator.nz(item.getQtyShipped());
            BigDecimal ordered = AmountCalculator.nz(item.getQtyOrdered());
            if (shipped.compareTo(BigDecimal.ZERO) > 0) {
                anyShipped = true;
            }
            if (QuantityPolicy.qtyOpen(ordered, shipped).compareTo(BigDecimal.ZERO) > 0) {
                allDone = false;
            }
        }
        if (allDone) {
            return OrderStatus.COMPLETED;
        }
        if (anyShipped) {
            return OrderStatus.PARTIAL_SHIPPED;
        }
        // 一条未出：草稿保持草稿，已确认保持已确认
        return current == OrderStatus.CONFIRMED ? OrderStatus.CONFIRMED : OrderStatus.DRAFT;
    }

    /**
     * 是否允许取消订单（取消前须先作废其下全部出货）。
     *
     * @param items 订单明细
     * @return true 表示可以取消
     */
    public static boolean canCancel(List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        return items.stream().noneMatch(i -> i != null && AmountCalculator.nz(i.getQtyShipped()).compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * 是否允许删除某条明细（已有出货的明细禁止删除，只能取消）。
     *
     * @param item 明细
     * @return true 表示可以删除
     */
    public static boolean canDeleteItem(SalesOrderItem item) {
        if (item == null) {
            return false;
        }
        return AmountCalculator.nz(item.getQtyShipped()).compareTo(BigDecimal.ZERO) == 0;
    }
}
