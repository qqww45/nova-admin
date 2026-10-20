package com.example.admin.domain.model.imports;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导入幂等策略（附录A A.1.5：每个模块可配置，默认跳过）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum IdempotentStrategy {

    /** 跳过：命中唯一键则计入跳过条数，不改动已有数据（默认） */
    SKIP("SKIP", "跳过"),

    /** 覆盖更新：命中唯一键则按新数据更新 */
    OVERWRITE("OVERWRITE", "覆盖更新"),

    /** 追加：不做唯一键判断，直接新增（仅在明确需要重复数据时使用） */
    APPEND("APPEND", "追加"),
    ;

    /** 存库编码 */
    private final String code;

    /** 界面标签 */
    private final String label;

    /** 默认策略 */
    public static final IdempotentStrategy DEFAULT = SKIP;

    /**
     * 按存库编码解析枚举，无法解析时回落默认策略（不让脏配置阻塞导入）。
     *
     * @param code 存库编码
     * @return 枚举，未匹配返回 {@link #SKIP}
     */
    public static IdempotentStrategy ofCode(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT;
        }
        for (IdempotentStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code.trim())) {
                return strategy;
            }
        }
        return DEFAULT;
    }
}
