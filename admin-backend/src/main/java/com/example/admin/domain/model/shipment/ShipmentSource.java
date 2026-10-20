package com.example.admin.domain.model.shipment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 出货记录来源（需求 5：界面模式与文档模式共用同一套规则，来源只用于追溯，不影响校验口径）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum ShipmentSource {

    /** 界面手工录入 */
    MANUAL("MANUAL", "手工"),

    /** Excel 导入 */
    IMPORT("IMPORT", "导入"),

    /** 剪贴板粘贴 */
    PASTE("PASTE", "粘贴"),
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
    public static ShipmentSource ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ShipmentSource source : values()) {
            if (source.code.equalsIgnoreCase(code.trim())) {
                return source;
            }
        }
        return null;
    }
}
