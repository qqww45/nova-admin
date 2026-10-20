package com.example.admin.domain.model.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单明细行状态（需求 4.1：订单头禁止删除已有出货的明细行，只能取消）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum ItemStatus {

    /** 正常 */
    NORMAL("NORMAL", "正常"),

    /** 已取消（取消前须先作废其下的出货记录） */
    CANCELED("CANCELED", "已取消"),
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
    public static ItemStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ItemStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
