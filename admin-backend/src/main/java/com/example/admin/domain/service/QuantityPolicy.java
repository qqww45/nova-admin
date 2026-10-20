package com.example.admin.domain.service;

import com.example.admin.common.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 数量规则引擎（需求 4.1）。
 *
 * <p>核心约束：
 * <ul>
 *     <li>{@code qty_open = qty_ordered − qty_shipped}，派生字段，页面与导出实时计算，禁止录入；</li>
 *     <li>新增出货必须满足 {@code 0 < qty ≤ qty_open}，超额默认禁止（[待确认] 容差另议）；</li>
 *     <li>出货保存成功后回写 {@code qty_shipped += qty}（同一事务）；</li>
 *     <li>禁止物理删除出货，只允许作废，作废回写扣减。</li>
 * </ul>
 *
 * <p>界面录入与 Excel 导入共用本类，杜绝"导入宽松、界面严格"的双标。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class QuantityPolicy {

    /** 数量小数位（[待确认] 允许小数，4 位） */
    public static final int QTY_SCALE = 4;

    private QuantityPolicy() {
    }

    /**
     * 计算未出货数（派生，永不落库为可编辑项）。
     *
     * @param qtyOrdered 下单数量
     * @param qtyShipped 累计出货数
     * @return 未出货数
     */
    public static BigDecimal qtyOpen(BigDecimal qtyOrdered, BigDecimal qtyShipped) {
        return AmountCalculator.nz(qtyOrdered)
                .subtract(AmountCalculator.nz(qtyShipped))
                .setScale(QTY_SCALE, AmountCalculator.ROUNDING);
    }

    /**
     * 校验出货数量是否合法，不合法直接抛业务异常（消息可直接展示给用户）。
     *
     * @param qty     本次出货数量
     * @param qtyOpen 该明细当前未出货数
     * @param label   用于提示的定位信息（如"第 3 行"或订单明细型号）
     * @throws BusinessException 数量非正数或超出未出货数
     */
    public static void validateShipQty(BigDecimal qty, BigDecimal qtyOpen, String label) {
        if (qty == null) {
            throw new BusinessException((label == null ? "" : label + "：") + "出货数量不能为空");
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException((label == null ? "" : label + "：") + "出货数量必须大于 0");
        }
        if (qty.compareTo(AmountCalculator.nz(qtyOpen)) > 0) {
            throw new BusinessException((label == null ? "" : label + "：")
                    + "出货数量 " + qty.stripTrailingZeros().toPlainString()
                    + " 超出未出货数 " + AmountCalculator.nz(qtyOpen).stripTrailingZeros().toPlainString()
                    + "，已拦截");
        }
    }

    /**
     * 校验下单数量是否合法。
     *
     * @param qtyOrdered 下单数量
     * @param label      定位信息
     * @throws BusinessException 数量非正数
     */
    public static void validateOrderQty(BigDecimal qtyOrdered, String label) {
        if (qtyOrdered == null || qtyOrdered.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException((label == null ? "" : label + "：") + "下单数量必须大于 0");
        }
    }

    /**
     * 新增出货后的累计出货数（回写值）。
     *
     * @param qtyShipped 原累计出货数
     * @param qty        本次出货数量
     * @return 回写后的累计出货数
     */
    public static BigDecimal addShipped(BigDecimal qtyShipped, BigDecimal qty) {
        return AmountCalculator.nz(qtyShipped).add(AmountCalculator.nz(qty)).setScale(QTY_SCALE, AmountCalculator.ROUNDING);
    }

    /**
     * 作废出货后的累计出货数（回写扣减值，不允许为负）。
     *
     * @param qtyShipped 原累计出货数
     * @param qty        被作废的出货数量
     * @return 扣减后的累计出货数（下限 0）
     */
    public static BigDecimal subtractShipped(BigDecimal qtyShipped, BigDecimal qty) {
        BigDecimal result = AmountCalculator.nz(qtyShipped).subtract(AmountCalculator.nz(qty));
        return result.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(QTY_SCALE, AmountCalculator.ROUNDING)
                : result.setScale(QTY_SCALE, AmountCalculator.ROUNDING);
    }
}
