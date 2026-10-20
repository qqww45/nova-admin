package com.example.admin.domain.service;

import com.example.admin.domain.model.order.ItemStatus;
import com.example.admin.domain.model.order.OrderStatus;
import com.example.admin.domain.model.order.SalesOrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 订单状态自动流转单元测试（需求 4.3）。 */
class OrderStatusPolicyTest {

    private static SalesOrderItem item(String qtyOrdered, String qtyShipped) {
        return item(qtyOrdered, qtyShipped, ItemStatus.NORMAL);
    }

    private static SalesOrderItem item(String qtyOrdered, String qtyShipped, ItemStatus status) {
        SalesOrderItem item = new SalesOrderItem();
        item.setQtyOrdered(new BigDecimal(qtyOrdered));
        item.setQtyShipped(new BigDecimal(qtyShipped));
        item.setStatus(status);
        item.recalculate();
        return item;
    }

    @Test
    @DisplayName("一条明细 0 < 出货数 < 下单数量 → 部分出货")
    void recalculate_partialShipped() {
        OrderStatus status = OrderStatusPolicy.recalculate(OrderStatus.CONFIRMED, List.of(item("100", "30")));
        assertThat(status).isEqualTo(OrderStatus.PARTIAL_SHIPPED);
    }

    @Test
    @DisplayName("所有有效明细未出货数为 0 → 已完成")
    void recalculate_allDone_completed() {
        OrderStatus status = OrderStatusPolicy.recalculate(
                OrderStatus.PARTIAL_SHIPPED, List.of(item("100", "100"), item("50", "50")));
        assertThat(status).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("部分完成 + 部分未出 → 仍是部分出货，不能算已完成")
    void recalculate_mixed_partialShipped() {
        OrderStatus status = OrderStatusPolicy.recalculate(
                OrderStatus.CONFIRMED, List.of(item("100", "100"), item("50", "0")));
        assertThat(status).isEqualTo(OrderStatus.PARTIAL_SHIPPED);
    }

    @Test
    @DisplayName("全部未出货：草稿保持草稿，已确认保持已确认（确认是人工动作，系统不代劳）")
    void recalculate_nothingShipped_keepsCurrent() {
        assertThat(OrderStatusPolicy.recalculate(OrderStatus.DRAFT, List.of(item("100", "0"))))
                .isEqualTo(OrderStatus.DRAFT);
        assertThat(OrderStatusPolicy.recalculate(OrderStatus.CONFIRMED, List.of(item("100", "0"))))
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("已取消是终态：出货/作废重算时不复活")
    void recalculate_canceled_isTerminal() {
        assertThat(OrderStatusPolicy.recalculate(OrderStatus.CANCELED, List.of(item("100", "0"))))
                .isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("已取消的明细不参与状态计算（只看有效明细）")
    void recalculate_canceledItemsIgnored() {
        OrderStatus status = OrderStatusPolicy.recalculate(
                OrderStatus.CONFIRMED, List.of(item("100", "100"), item("50", "0", ItemStatus.CANCELED)));
        assertThat(status).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("全部明细被取消 → 订单自动置为已取消")
    void recalculate_allItemsCanceled_orderCanceled() {
        OrderStatus status = OrderStatusPolicy.recalculate(
                OrderStatus.CONFIRMED, List.of(item("50", "0", ItemStatus.CANCELED)));
        assertThat(status).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("无明细：不做流转，保持原状")
    void recalculate_noItems_keepsCurrent() {
        assertThat(OrderStatusPolicy.recalculate(OrderStatus.DRAFT, List.of())).isEqualTo(OrderStatus.DRAFT);
        assertThat(OrderStatusPolicy.recalculate(null, null)).isEqualTo(OrderStatus.DRAFT);
    }

    @Test
    @DisplayName("取消校验：存在已出货明细时不允许取消（须先作废出货）")
    void canCancel_blockedWhenShipped() {
        assertThat(OrderStatusPolicy.canCancel(List.of(item("100", "0")))).isTrue();
        assertThat(OrderStatusPolicy.canCancel(List.of(item("100", "30")))).isFalse();
        assertThat(OrderStatusPolicy.canCancel(List.of())).isTrue();
    }

    @Test
    @DisplayName("明细删除校验：已出货的明细只能取消，不能删除")
    void canDeleteItem_blockedWhenShipped() {
        assertThat(OrderStatusPolicy.canDeleteItem(item("100", "0"))).isTrue();
        assertThat(OrderStatusPolicy.canDeleteItem(item("100", "30"))).isFalse();
    }

    @Test
    @DisplayName("明细重算：未出货数与含税标记随税率联动")
    void itemRecalculate_derivedFields() {
        SalesOrderItem item = new SalesOrderItem();
        item.setModel("ABC-100");
        item.setQtyOrdered(new BigDecimal("10"));
        item.setQtyShipped(new BigDecimal("4"));
        item.setUnitPrice(new BigDecimal("113"));
        item.setTaxRate(new BigDecimal("0.13"));
        item.recalculate();

        assertThat(item.getQtyOpen()).isEqualByComparingTo("6");
        assertThat(item.getPriceIsTaxIncluded()).isEqualTo(1);
        assertThat(item.getAmountTaxIncluded()).isEqualByComparingTo("1130.00");
        assertThat(item.getAmountExTax()).isEqualByComparingTo("1000.00");
        assertThat(item.getTaxAmount()).isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("明细登记出货：校验通过并回写，超量直接抛异常")
    void itemApplyShipment_writesBackAndBlocksOver() {
        SalesOrderItem item = item("100", "0");
        item.applyShipment(new BigDecimal("30"));
        assertThat(item.getQtyShipped()).isEqualByComparingTo("30");
        assertThat(item.getQtyOpen()).isEqualByComparingTo("70");
        assertThat(item.isFullyShipped()).isFalse();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> item.applyShipment(new BigDecimal("71")))
                .isInstanceOf(com.example.admin.common.exception.BusinessException.class);
    }
}
