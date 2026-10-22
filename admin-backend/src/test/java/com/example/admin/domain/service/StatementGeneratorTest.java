package com.example.admin.domain.service;

import com.example.admin.domain.model.reconciliation.StatementLineType;
import com.example.admin.domain.model.shipment.ShipmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 对账单聚合引擎单元测试（客户应收账款对账单核心口径，必须覆盖到位）。 */
class StatementGeneratorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static StatementGenerator.ShipmentFact ship(String docNo, String qty, String amount, ShipmentType type) {
        return new StatementGenerator.ShipmentFact(
                1L, type, LocalDateTime.of(2026, 4, 11, 0, 0), docNo, "PO1", "M1", "主板", "G138", "pcs",
                bd(qty), bd("432"), bd(amount), null);
    }

    private static StatementGenerator.ReceiptFact receipt(String docNo, String amount) {
        return new StatementGenerator.ReceiptFact(1L, LocalDateTime.of(2026, 1, 31, 0, 0), docNo, bd(amount), null);
    }

    @Test
    @DisplayName("恒等式：期末应收 = 期初 + 出货 − 退货 − 收款")
    void generate_identity_holds() {
        // 期初 1000，出货 963176，退货 832032（退厂），收款 990000 → 期末 = 1000 + 963176 − 832032 − 990000 = −857856
        List<StatementGenerator.ShipmentFact> ships = List.of(
                ship("XSCKD1", "2219", "963176", ShipmentType.SHIP),
                ship("CGTL1", "3852", "832032", ShipmentType.RETURN_FACTORY));
        List<StatementGenerator.ReceiptFact> receipts = List.of(receipt("R1", "990000"));

        StatementGenerator.StatementResult result = StatementGenerator.generate(bd("1000"), ships, receipts);
        StatementGenerator.StatementSummary s = result.summary();

        assertThat(s.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(s.shippedAmount()).isEqualByComparingTo("963176.00");
        assertThat(s.returnedAmount()).isEqualByComparingTo("832032.00");
        assertThat(s.receivedAmount()).isEqualByComparingTo("990000.00");
        assertThat(s.endingBalance()).isEqualByComparingTo("-857856.00");
    }

    @Test
    @DisplayName("退货/退厂与收款明细取负向：数量与金额均为负")
    void generate_returnAndReceipt_areNegative() {
        List<StatementGenerator.ShipmentFact> ships = List.of(
                ship("XSCKD1", "10", "4320", ShipmentType.SHIP),
                ship("XSTHD1", "5", "2160", ShipmentType.RETURN));
        List<StatementGenerator.ReceiptFact> receipts = List.of(receipt("R1", "1000"));

        StatementGenerator.StatementResult result = StatementGenerator.generate(BigDecimal.ZERO, ships, receipts);

        // 生成器按业务日期排序：收款(1/31) 早于两条出货(4/11)，故按行类型定位断言
        StatementGenerator.StatementLine shipLine = result.lines().stream()
                .filter(l -> l.lineType() == StatementLineType.SHIP).findFirst().orElseThrow();
        StatementGenerator.StatementLine returnLine = result.lines().stream()
                .filter(l -> l.lineType() == StatementLineType.RETURN).findFirst().orElseThrow();
        StatementGenerator.StatementLine receiveLine = result.lines().stream()
                .filter(l -> l.lineType() == StatementLineType.RECEIVE).findFirst().orElseThrow();

        assertThat(shipLine.qty()).isEqualByComparingTo("10");
        assertThat(shipLine.amount()).isEqualByComparingTo("4320.00");

        assertThat(returnLine.qty()).isEqualByComparingTo("-5");
        assertThat(returnLine.amount()).isEqualByComparingTo("-2160.00");

        assertThat(receiveLine.amount()).isEqualByComparingTo("-1000.00");
    }

    @Test
    @DisplayName("明细按业务日期升序排列，sortNo 从 1 重排")
    void generate_linesSortedByDateWithSortNo() {
        StatementGenerator.ShipmentFact later = new StatementGenerator.ShipmentFact(
                1L, ShipmentType.SHIP, LocalDateTime.of(2026, 5, 1, 0, 0), "A", "PO", "M", "n", "s", "u",
                bd("1"), bd("100"), bd("100"), null);
        StatementGenerator.ShipmentFact earlier = new StatementGenerator.ShipmentFact(
                2L, ShipmentType.SHIP, LocalDateTime.of(2026, 3, 1, 0, 0), "B", "PO", "M", "n", "s", "u",
                bd("1"), bd("100"), bd("100"), null);

        StatementGenerator.StatementResult result = StatementGenerator.generate(
                BigDecimal.ZERO, List.of(later, earlier), List.of());

        assertThat(result.lines().get(0).docNo()).isEqualTo("B");
        assertThat(result.lines().get(0).sortNo()).isEqualTo(1);
        assertThat(result.lines().get(1).docNo()).isEqualTo("A");
        assertThat(result.lines().get(1).sortNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("空值安全：期初/出货/收款为空不抛异常，按 0 处理")
    void generate_nullSafe() {
        StatementGenerator.StatementResult result = StatementGenerator.generate(null, null, null);
        assertThat(result.lines()).isEmpty();
        assertThat(result.summary().endingBalance()).isEqualByComparingTo("0.00");
        assertThat(result.summary().openingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("仅出货无退货收款：期末 = 期初 + 出货")
    void generate_onlyShipment() {
        List<StatementGenerator.ShipmentFact> ships = List.of(ship("XSCKD1", "100", "43200", ShipmentType.SHIP));
        StatementGenerator.StatementResult result = StatementGenerator.generate(bd("5000"), ships, List.of());
        assertThat(result.summary().shippedAmount()).isEqualByComparingTo("43200.00");
        assertThat(result.summary().endingBalance()).isEqualByComparingTo("48200.00");
    }
}
