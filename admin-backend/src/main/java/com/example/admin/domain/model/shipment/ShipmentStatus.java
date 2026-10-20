package com.example.admin.domain.model.shipment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 出货记录状态（需求 4.1：禁止物理删除出货记录，只允许作废/红冲）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum ShipmentStatus {

    /** 正常 */
    NORMAL("NORMAL", "正常"),

    /** 已作废：作废时回写扣减明细出货数，记录本身保留 */
    VOID("VOID", "已作废"),
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
    public static ShipmentStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ShipmentStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
