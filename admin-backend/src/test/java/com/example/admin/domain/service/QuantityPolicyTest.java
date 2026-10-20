package com.example.admin.domain.service;

import com.example.admin.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 数量规则单元测试（需求 4.1：未出货数派生、超额拦截、出货回写、作废扣减）。 */
class QuantityPolicyTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("未出货数 = 下单数量 − 出货数")
    void qtyOpen_isOrderedMinusShipped() {
        assertThat(QuantityPolicy.qtyOpen(bd("100"), bd("30"))).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("未出货数：未出过货时等于下单数量，空值按 0 处理")
    void qtyOpen_edgeCases() {
        assertThat(QuantityPolicy.qtyOpen(bd("100"), null)).isEqualByComparingTo("100");
        assertThat(QuantityPolicy.qtyOpen(null, bd("10"))).isEqualByComparingTo("-10");
    }

    @Test
    @DisplayName("出货数量刚好等于未出货数：允许")
    void validateShipQty_equalToOpen_passes() {
        QuantityPolicy.validateShipQty(bd("70"), bd("70"), "第 1 行");
    }

    @Test
    @DisplayName("出货数量超过未出货数：拦截并给出可读提示")
    void validateShipQty_overOpen_rejected() {
        assertThatThrownBy(() -> QuantityPolicy.validateShipQty(bd("71"), bd("70"), "第 1 行"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出未出货数")
                .hasMessageContaining("71")
                .hasMessageContaining("70");
    }

    @Test
    @DisplayName("出货数量为 0 或负数：拦截")
    void validateShipQty_nonPositive_rejected() {
        assertThatThrownBy(() -> QuantityPolicy.validateShipQty(bd("0"), bd("70"), "第 1 行"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须大于 0");
        assertThatThrownBy(() -> QuantityPolicy.validateShipQty(bd("-1"), bd("70"), "第 1 行"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须大于 0");
    }

    @Test
    @DisplayName("出货数量为空：拦截（导入时单元格为空也应报错而非静默为 0）")
    void validateShipQty_null_rejected() {
        assertThatThrownBy(() -> QuantityPolicy.validateShipQty(null, bd("70"), "第 1 行"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("下单数量必须大于 0")
    void validateOrderQty_mustBePositive() {
        QuantityPolicy.validateOrderQty(bd("0.0001"), "第 1 行");
        assertThatThrownBy(() -> QuantityPolicy.validateOrderQty(bd("0"), "第 1 行"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("出货回写：累计出货数按数量累加（可分多次出货）")
    void addShipped_accumulates() {
        BigDecimal after1 = QuantityPolicy.addShipped(bd("0"), bd("30"));
        BigDecimal after2 = QuantityPolicy.addShipped(after1, bd("20"));
        assertThat(after1).isEqualByComparingTo("30");
        assertThat(after2).isEqualByComparingTo("50");
        assertThat(QuantityPolicy.qtyOpen(bd("100"), after2)).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("作废扣减：出货数回退，且不会出现负数")
    void subtractShipped_revertsAndNeverNegative() {
        assertThat(QuantityPolicy.subtractShipped(bd("50"), bd("20"))).isEqualByComparingTo("30");
        assertThat(QuantityPolicy.subtractShipped(bd("10"), bd("999"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("完整链路：下单 100 → 出 30 → 出 30 → 作废 30 → 未出货数回到 70")
    void fullLifecycle_openQuantityReturns() {
        BigDecimal ordered = bd("100");
        BigDecimal shipped = BigDecimal.ZERO;
        shipped = QuantityPolicy.addShipped(shipped, bd("30"));
        assertThat(QuantityPolicy.qtyOpen(ordered, shipped)).isEqualByComparingTo("70");
        shipped = QuantityPolicy.addShipped(shipped, bd("30"));
        assertThat(QuantityPolicy.qtyOpen(ordered, shipped)).isEqualByComparingTo("40");
        shipped = QuantityPolicy.subtractShipped(shipped, bd("30"));
        assertThat(QuantityPolicy.qtyOpen(ordered, shipped)).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("小数数量：允许 4 位小数")
    void decimalQuantity_supported() {
        BigDecimal shipped = QuantityPolicy.addShipped(bd("0"), bd("0.0001"));
        assertThat(shipped).isEqualByComparingTo("0.0001");
        assertThat(QuantityPolicy.qtyOpen(bd("1"), shipped)).isEqualByComparingTo("0.9999");
    }
}
