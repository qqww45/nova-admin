package com.example.admin.domain.model.receipt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收款状态（V13 新增，{@code biz_receipt.status}）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Getter
@AllArgsConstructor
public enum ReceiptStatus {

    /** 正常 */
    NORMAL("NORMAL", "正常"),

    /** 已作废：作废时回写扣减已分配金额，记录本身保留（禁止物理删除） */
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
    public static ReceiptStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ReceiptStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
