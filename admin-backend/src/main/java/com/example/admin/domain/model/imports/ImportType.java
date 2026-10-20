package com.example.admin.domain.model.imports;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导入模块类型（与 biz_import_mapping.module、biz_import_batch.batch_type 一致）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum ImportType {

    /** 客户导入 */
    CUSTOMER("CUSTOMER", "客户"),

    /** 订单导入（头 + 明细合一） */
    ORDER("ORDER", "订单"),

    /** 出货导入 */
    SHIPMENT("SHIPMENT", "出货"),
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
    public static ImportType ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ImportType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }
}
