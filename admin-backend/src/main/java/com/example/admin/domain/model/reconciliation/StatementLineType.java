package com.example.admin.domain.model.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对账单明细行类型（V13 新增，{@code biz_statement_line.line_type}）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Getter
@AllArgsConstructor
public enum StatementLineType {

    /** 出货（正向金额） */
    SHIP("SHIP", "出货"),

    /** 退货 / 退厂（负向金额） */
    RETURN("RETURN", "退货"),

    /** 收款（负向金额，冲减应收） */
    RECEIVE("RECEIVE", "收款"),
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
    public static StatementLineType ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (StatementLineType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }
}
