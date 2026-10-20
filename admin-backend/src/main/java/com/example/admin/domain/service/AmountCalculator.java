package com.example.admin.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额 / 税额计算规则引擎（需求 4.2，本系统最核心的口径）。
 *
 * <p>规则（tax_rate 为空或 = 0 → 单价视为<b>不含税</b>；tax_rate > 0 → 单价视为<b>含税</b>）：
 * <pre>
 *   不含税：amount_ex_tax       = round(unit_price × qty)
 *           tax_amount          = 0
 *           amount_tax_included = amount_ex_tax
 *   含  税：amount_tax_included = round(unit_price × qty)
 *           amount_ex_tax       = round(amount_tax_included ÷ (1 + tax_rate))
 *           tax_amount          = amount_tax_included − amount_ex_tax   // 倒挤，保证三项恒等
 * </pre>
 *
 * <p>取舍：
 * <ul>
 *     <li>全程 {@link BigDecimal}，禁止 float/double，避免 0.1+0.2 类误差污染对账；</li>
 *     <li>金额统一四舍五入到 2 位（{@link #MONEY_SCALE}），展示与导出取 2 位；</li>
 *     <li>税额用"价税合计 − 不含税金额"倒挤而不是单独计算，
 *         这样 {@code amount_ex_tax + tax_amount == amount_tax_included} 恒成立，
 *         财务对账时不会出现"三分钱"差。</li>
 * </ul>
 *
 * <p>本类是纯函数，无外部依赖，界面模式与文档模式（导入/粘贴）共用同一入口。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class AmountCalculator {

    /** 金额小数位（展示与落库一致） */
    public static final int MONEY_SCALE = 2;

    /** 舍入模式：四舍五入（与财务习惯一致） */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private AmountCalculator() {
    }

    /**
     * 金额三元组（不含税金额 / 税额 / 价税合计）。
     *
     * @param amountExTax       不含税金额
     * @param taxAmount         税额
     * @param amountTaxIncluded 价税合计
     */
    public record Amounts(BigDecimal amountExTax, BigDecimal taxAmount, BigDecimal amountTaxIncluded) {
    }

    /**
     * 判断单价口径：税率非空且 &gt; 0 时为含税单价。
     *
     * @param taxRate 税率（0.13 表示 13%）
     * @return true 表示含税单价
     */
    public static boolean isTaxIncluded(BigDecimal taxRate) {
        return taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 按明细口径计算金额三元组。
     *
     * @param unitPrice 单价（含税与否由 taxRate 决定）
     * @param qty       数量
     * @param taxRate   税率（可为空，空视为不含税）
     * @return 金额三元组（三项均为 2 位小数，恒等关系成立）
     */
    public static Amounts calculate(BigDecimal unitPrice, BigDecimal qty, BigDecimal taxRate) {
        BigDecimal price = nz(unitPrice);
        BigDecimal quantity = nz(qty);
        BigDecimal total = price.multiply(quantity).setScale(MONEY_SCALE, ROUNDING);

        if (!isTaxIncluded(taxRate)) {
            // 不含税：价税合计就等于不含税金额，税额为 0
            return new Amounts(total, BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING), total);
        }
        // 含税：先由价税合计反算不含税金额，再用倒挤得到税额
        BigDecimal divisor = BigDecimal.ONE.add(nz(taxRate));
        BigDecimal exTax = total.divide(divisor, MONEY_SCALE, ROUNDING);
        return new Amounts(exTax, total.subtract(exTax), total);
    }

    /**
     * 按出货数量计算本次出货的金额快照（口径与所属明细完全一致）。
     *
     * <p>[待确认] 出货是否需要记录金额 —— 附录A 建议"记录快照，便于对账"，本实现已按该建议落地。
     *
     * @param unitPrice 明细单价
     * @param qty       本次出货数量
     * @param taxRate   明细税率
     * @return 金额三元组
     */
    public static Amounts forShipment(BigDecimal unitPrice, BigDecimal qty, BigDecimal taxRate) {
        return calculate(unitPrice, qty, taxRate);
    }

    /**
     * 四舍五入到金额精度。
     *
     * @param value 原值（可为空）
     * @return 2 位小数结果（空返回 0.00）
     */
    public static BigDecimal roundMoney(BigDecimal value) {
        return nz(value).setScale(MONEY_SCALE, ROUNDING);
    }

    /**
     * 空值归零（金额场景统一按 0 处理，避免 NPE 打断批量校验）。
     *
     * @param value 原值
     * @return 非空值
     */
    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
