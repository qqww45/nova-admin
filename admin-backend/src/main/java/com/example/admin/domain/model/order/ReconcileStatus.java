package com.example.admin.domain.model.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 明细对账状态（见需求 4.4：对账日期在明细级填写，填写后即为已对账）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum ReconcileStatus {

    /** 未对账 */
    UNRECONCILED("UNRECONCILED", "未对账"),

    /** 已对账 */
    RECONCILED("RECONCILED", "已对账"),
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
    public static ReconcileStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ReconcileStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
