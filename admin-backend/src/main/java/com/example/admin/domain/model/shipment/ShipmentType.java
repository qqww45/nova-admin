package com.example.admin.domain.model.shipment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 出货方向（V13 新增）。
 *
 * <p>原 {@code biz_shipment} 只有"出货 + 作废红冲"，无法表达对账单里的销售退货 / 退厂返工。
 * 对账单样本（XSTHD 销售退货、CGTL 退工厂返工）均作为负数明细行，
 * 因此需显式区分方向，退货/退厂的金额与数量在对账单中统一取负向值。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Getter
@AllArgsConstructor
public enum ShipmentType {

    /** 出货（正向） */
    SHIP("SHIP", "出货"),

    /** 销售退货（如 XSTHD…） */
    RETURN("RETURN", "销售退货"),

    /** 退厂返工（如 CGTL…，负数行） */
    RETURN_FACTORY("RETURN_FACTORY", "退厂返工"),
    ;

    /** 存库编码 */
    private final String code;

    /** 界面标签 */
    private final String label;

    /**
     * 按存库编码解析枚举。
     *
     * @param code 存库编码
     * @return 枚举，未匹配返回 null
     */
    public static ShipmentType ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ShipmentType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }

    /**
     * 是否退货类（销售退货 / 退厂返工），对账单中按负向处理。
     *
     * @return true 表示退货类
     */
    public boolean isReturn() {
        return this == RETURN || this == RETURN_FACTORY;
    }
}
