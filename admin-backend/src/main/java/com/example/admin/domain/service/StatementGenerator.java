package com.example.admin.domain.service;

import com.example.admin.domain.model.reconciliation.StatementLineType;
import com.example.admin.domain.model.shipment.ShipmentType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 对账单聚合引擎（客户应收账款对账单的核心口径）。
 *
 * <p>恒等式（与样本《一恒科&鸿晟达 对账》一致）：
 * <pre>
 *   期末应收 = 期初应收 + 本期出货 − 本期退货/退厂 − 本期收款
 * </pre>
 *
 * <p>设计要点：
 * <ul>
 *     <li>纯函数，无外部依赖，界面模式与导入/接口模式共用同一入口（沿用 AmountCalculator 思路）；</li>
 *     <li>输入为"已过滤掉作废/红冲"的事实（VOID 由应用层在调用前剔除），本类只负责聚合与取负向；</li>
 *     <li>退货 / 退厂 / 收款在对账单明细中一律取<b>负向</b>数量与金额，与样本负数行的展示一致；</li>
 *     <li>金额统一四舍五入到 2 位（{@link #MONEY_SCALE}），全程 {@link BigDecimal}，禁止浮点。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-10-22
 */
public final class StatementGenerator {

    /** 金额小数位（展示与落库一致） */
    public static final int MONEY_SCALE = 2;

    /** 舍入模式：四舍五入 */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private StatementGenerator() {
    }

    /** 出货事实（应用层从 biz_shipment 转换后传入，已剔除作废） */
    public record ShipmentFact(
            Long id,
            ShipmentType type,
            LocalDateTime bizDate,
            String docNo,
            String customerOrderNo,
            String materialNo,
            String materialName,
            String spec,
            String unit,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal amountTaxIncluded,
            String remark) {
    }

    /** 收款事实（应用层从 biz_receipt 转换后传入，已剔除作废） */
    public record ReceiptFact(
            Long id,
            LocalDateTime bizDate,
            String docNo,
            BigDecimal amount,
            String remark) {
    }

    /** 对账单明细行（快照，落库到 biz_statement_line） */
    public record StatementLine(
            StatementLineType lineType,
            LocalDateTime bizDate,
            String docNo,
            String customerOrderNo,
            String materialNo,
            String materialName,
            String spec,
            String unit,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal amount,
            Long sourceId,
            int sortNo,
            String remark) {
    }

    /** 对账单汇总（四要素 + 期末余额） */
    public record StatementSummary(
            BigDecimal openingBalance,
            BigDecimal shippedAmount,
            BigDecimal returnedAmount,
            BigDecimal receivedAmount,
            BigDecimal endingBalance) {
    }

    /** 对账单整体结果 */
    public record StatementResult(List<StatementLine> lines, StatementSummary summary) {
    }

    /**
     * 按客户 + 期间聚合对账单。
     *
     * @param openingBalance 期初应收（可为空，按 0 处理）
     * @param shipments      期间内的出货事实（已剔除作废；退货/退厂通过 {@link ShipmentType} 区分）
     * @param receipts       期间内的收款事实（已剔除作废）
     * @return 对账单明细与汇总
     */
    public static StatementResult generate(BigDecimal openingBalance,
                                          List<ShipmentFact> shipments,
                                          List<ReceiptFact> receipts) {
        List<StatementLine> lines = new ArrayList<>();
        BigDecimal shipped = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;

        for (ShipmentFact s : nz(shipments)) {
            BigDecimal amount = round(nz(s.amountTaxIncluded()));
            BigDecimal qty = s.qty() == null ? BigDecimal.ZERO : s.qty();
            if (s.type() != null && s.type().isReturn()) {
                // 退货/退厂：数量与金额取负向（与样本 XSTHD/CGTL 负数行一致）
                lines.add(new StatementLine(
                        StatementLineType.RETURN, s.bizDate(), s.docNo(), s.customerOrderNo(),
                        s.materialNo(), s.materialName(), s.spec(), s.unit(),
                        qty.negate(), nz(s.unitPrice()), amount.negate(), s.id(), 0, s.remark()));
                returned = returned.add(amount.abs());
            } else {
                lines.add(new StatementLine(
                        StatementLineType.SHIP, s.bizDate(), s.docNo(), s.customerOrderNo(),
                        s.materialNo(), s.materialName(), s.spec(), s.unit(),
                        qty, nz(s.unitPrice()), amount, s.id(), 0, s.remark()));
                shipped = shipped.add(amount);
            }
        }

        for (ReceiptFact r : nz(receipts)) {
            BigDecimal amount = round(nz(r.amount()));
            // 收款减少应收：金额取负向
            lines.add(new StatementLine(
                    StatementLineType.RECEIVE, r.bizDate(), r.docNo(), null, null, null, null, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, amount.negate(), r.id(), 0, r.remark()));
            received = received.add(amount);
        }

        // 按业务日期升序排列（空日期排最前），并重排 sortNo
        lines.sort(Comparator.comparing(
                (StatementLine l) -> l.bizDate() == null ? LocalDateTime.MIN : l.bizDate()));
        List<StatementLine> sorted = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            StatementLine l = lines.get(i);
            sorted.add(new StatementLine(
                    l.lineType(), l.bizDate(), l.docNo(), l.customerOrderNo(),
                    l.materialNo(), l.materialName(), l.spec(), l.unit(),
                    l.qty(), l.unitPrice(), l.amount(), l.sourceId(), i + 1, l.remark()));
        }

        BigDecimal opening = round(nz(openingBalance));
        // 期末应收 = 期初 + 出货 − 退货 − 收款
        BigDecimal ending = opening.add(shipped).subtract(returned).subtract(received);
        StatementSummary summary = new StatementSummary(opening, shipped, returned, received, ending);
        return new StatementResult(sorted, summary);
    }

    /**
     * 四舍五入到金额精度。
     *
     * @param value 原值（可为空）
     * @return 2 位小数结果
     */
    public static BigDecimal round(BigDecimal value) {
        return nz(value).setScale(MONEY_SCALE, ROUNDING);
    }

    /**
     * 空值安全：列表为空按空列表处理。
     *
     * @param list 原列表
     * @param <T>  元素类型
     * @return 非空列表
     */
    private static <T> List<T> nz(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * 空值归零。
     *
     * @param value 原值
     * @return 非空值
     */
    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
