package com.example.admin.domain.model.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对账单状态（V13 新增，{@code biz_statement.status}）。
 *
 * <p>流转：草稿 → 已签发（发给客户）→ 已确认（客户回签）/ 有差异。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Getter
@AllArgsConstructor
public enum StatementStatus {

    /** 草稿：生成后尚未签发 */
    DRAFT("DRAFT", "草稿"),

    /** 已签发：已发给客户核对 */
    ISSUED("ISSUED", "已签发"),

    /** 已确认：客户回签无误 */
    CONFIRMED("CONFIRMED", "已确认"),

    /** 有差异：客户提出差异，需跟进 */
    DIFFERENT("DIFFERENT", "有差异"),
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
    public static StatementStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (StatementStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
