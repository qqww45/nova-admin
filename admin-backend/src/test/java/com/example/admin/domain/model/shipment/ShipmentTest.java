package com.example.admin.domain.model.shipment;

import com.example.admin.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出货实体测试：补挂订单明细与无源判定。
 *
 * <p>异常数据池的修复动作看着简单（填个明细 ID），
 * 但"已作废的出货能不能补挂"这种边界一旦放开，
 * 就会把一笔已经红冲的业务又算回客户账上，必须挡住。
 *
 * @author admin-system
 * @since 2026-01-01
 */
class ShipmentTest {

    private Shipment shipment() {
        Shipment shipment = new Shipment();
        shipment.setQty(new BigDecimal("10"));
        shipment.setStatus(ShipmentStatus.NORMAL);
        return shipment;
    }

    @Test
    @DisplayName("无源出货：未关联明细时 isOrphan 为真")
    void withoutItemShouldBeOrphan() {
        assertTrue(shipment().isOrphan());
    }

    @Test
    @DisplayName("补挂明细后不再属于异常池")
    void linkToShouldClearOrphan() {
        Shipment shipment = shipment();
        shipment.linkTo(1001L);
        assertEquals(1001L, shipment.getOrderItemId());
        assertFalse(shipment.isOrphan());
    }

    @Test
    @DisplayName("未选择明细时拒绝补挂")
    void linkToNullShouldFail() {
        assertThrows(BusinessException.class, () -> shipment().linkTo(null));
    }

    @Test
    @DisplayName("已作废的出货不允许补挂：避免红冲业务被重新计入")
    void linkToVoidedShouldFail() {
        Shipment shipment = shipment();
        shipment.void_("录入错误");
        assertTrue(shipment.isVoid());
        assertThrows(BusinessException.class, () -> shipment.linkTo(1001L));
    }

    @Test
    @DisplayName("幂等键：数量相同但日期不同视为两笔")
    void idempotentKeyShouldIncludeDate() {
        Shipment a = shipment();
        a.setProcessOrderNo("WK-1");
        a.setCustomerOrderNo("PO-1");
        a.setMaterialNo("M-1");
        a.setShipDate(java.time.LocalDateTime.of(2026, 10, 1, 0, 0));

        Shipment b = shipment();
        b.setProcessOrderNo("WK-1");
        b.setCustomerOrderNo("PO-1");
        b.setMaterialNo("M-1");
        b.setShipDate(java.time.LocalDateTime.of(2026, 10, 2, 0, 0));

        assertTrue(!a.idempotentKey().equals(b.idempotentKey()));
    }
}
