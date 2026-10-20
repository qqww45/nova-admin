package com.example.admin.domain.model.imports;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导入批次状态（支持"仅校验不导入"与"一键回滚"）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum BatchStatus {

    /** 仅校验：未落库（预览阶段的报告） */
    PREVIEW("PREVIEW", "仅校验"),

    /** 已导入 */
    COMMITTED("COMMITTED", "已导入"),

    /** 已回滚：该批次的新增记录已被撤销（仅限本批次增量） */
    ROLLED_BACK("ROLLED_BACK", "已回滚"),
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
    public static BatchStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (BatchStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
