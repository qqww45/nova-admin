package com.example.admin.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 金额 / 税额计算单元测试（需求 4.2，本系统最核心的口径，必须覆盖到位）。 */
class AmountCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("税率为空：单价视为不含税，税额为 0，价税合计等于不含税金额")
    void calculate_taxRateNull_treatedAsExTax() {
        AmountCalculator.Amounts amounts = AmountCalculator.calculate(bd("10"), bd("3"), null);
        assertThat(amounts.amountExTax()).isEqualByComparingTo("30.00");
        assertThat(amounts.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(amounts.amountTaxIncluded()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("税率为 0：与空等价，按不含税口径计算")
    void calculate_taxRateZero_treatedAsExTax() {
        AmountCalculator.Amounts amounts = AmountCalculator.calculate(bd("10"), bd("3"), BigDecimal.ZERO);
        assertThat(amounts.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(amounts.amountTaxIncluded()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("税率 13%：单价视为含税，先算价税合计再反算不含税金额，税额倒挤")
    void calculate_taxRate13_priceIsTaxIncluded() {
        AmountCalculator.Amounts amounts = AmountCalculator.calculate(bd("113"), bd("2"), bd("0.13"));
        // 价税合计 = 113 × 2 = 226.00
        assertThat(amounts.amountTaxIncluded()).isEqualByComparingTo("226.00");
        // 不含税 = 226 / 1.13 = 200.00
        assertThat(amounts.amountExTax()).isEqualByComparingTo("200.00");
        // 税额倒挤 = 26.00
        assertThat(amounts.taxAmount()).isEqualByComparingTo("26.00");
    }

    @Test
    @DisplayName("恒等式：不含税金额 + 税额 == 价税合计（倒挤的意义就在这里）")
    void calculate_identity_alwaysHolds() {
        String[] rates = {"0.01", "0.03", "0.06", "0.09", "0.13"};
        for (String rate : rates) {
            for (String price : new String[]{"0.01", "1.5", "99.99", "1234.5678"}) {
                for (String qty : new String[]{"1", "3", "7.5"}) {
                    AmountCalculator.Amounts amounts = AmountCalculator.calculate(bd(price), bd(qty), bd(rate));
                    assertThat(amounts.amountExTax().add(amounts.taxAmount()))
                            .as("税率 %s 单价 %s 数量 %s", rate, price, qty)
                            .isEqualByComparingTo(amounts.amountTaxIncluded());
                }
            }
        }
    }

    @Test
    @DisplayName("四舍五入：金额统一保留 2 位，HALF_UP")
    void calculate_rounding_halfUpToTwoScale() {
        // 10.005 × 3 = 30.015 → 30.02
        AmountCalculator.Amounts amounts = AmountCalculator.calculate(bd("10.005"), bd("3"), null);
        assertThat(amounts.amountExTax()).isEqualByComparingTo("30.02");
        assertThat(amounts.amountExTax().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("含税且不能整除：税额由倒挤得出，不出现三分钱差")
    void calculate_notDivisible_taxByBackCalculation() {
        // 价税合计 = 100 × 1 = 100.00；不含税 = 100 / 1.03 = 97.09（四舍五入）；税额 = 2.91
        AmountCalculator.Amounts amounts = AmountCalculator.calculate(bd("100"), bd("1"), bd("0.03"));
        assertThat(amounts.amountTaxIncluded()).isEqualByComparingTo("100.00");
        assertThat(amounts.amountExTax()).isEqualByComparingTo("97.09");
        assertThat(amounts.taxAmount()).isEqualByComparingTo("2.91");
    }

    @Test
    @DisplayName("isTaxIncluded：只有税率非空且大于 0 才算含税")
    void isTaxIncluded_onlyPositiveRate() {
        assertThat(AmountCalculator.isTaxIncluded(null)).isFalse();
        assertThat(AmountCalculator.isTaxIncluded(BigDecimal.ZERO)).isFalse();
        assertThat(AmountCalculator.isTaxIncluded(bd("0.13"))).isTrue();
    }

    @Test
    @DisplayName("出货金额快照：口径与所属明细一致")
    void forShipment_sameRuleAsOrderItem() {
        AmountCalculator.Amounts full = AmountCalculator.calculate(bd("113"), bd("10"), bd("0.13"));
        AmountCalculator.Amounts part = AmountCalculator.forShipment(bd("113"), bd("3"), bd("0.13"));
        assertThat(part.amountTaxIncluded()).isEqualByComparingTo("339.00");
        // 分三次出 3 + 3 + 4，合计应等于整单（口径一致性的体现）
        AmountCalculator.Amounts part2 = AmountCalculator.forShipment(bd("113"), bd("3"), bd("0.13"));
        AmountCalculator.Amounts part3 = AmountCalculator.forShipment(bd("113"), bd("4"), bd("0.13"));
        assertThat(part.amountExTax().add(part2.amountExTax()).add(part3.amountExTax()))
                .isEqualByComparingTo(full.amountExTax());
    }

    @Test
    @DisplayName("空值安全：单价或数量为空按 0 处理，不抛异常打断批量校验")
    void calculate_nullSafe() {
        AmountCalculator.Amounts amounts = AmountCalculator.calculate(null, bd("5"), bd("0.13"));
        assertThat(amounts.amountTaxIncluded()).isEqualByComparingTo("0.00");
    }
}
