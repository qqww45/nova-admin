package com.example.admin.domain.model.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态（见需求 4.3：状态由服务端在出货/作废后重算，前端禁止直接改状态字段）。
 *
 * <p>取值说明：
 * <ul>
 *     <li>{@link #DRAFT} 草稿：新建未确认；</li>
 *     <li>{@link #CONFIRMED} 已确认：手工确认；</li>
 *     <li>{@link #PARTIAL_SHIPPED} 部分出货：存在明细 {@code 0 < qty_shipped < qty_ordered}；</li>
 *     <li>{@link #COMPLETED} 已完成：所有有效明细 {@code qty_open = 0}；</li>
 *     <li>{@link #CANCELED} 已取消：手工取消（须先作废其下出货）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    /** 草稿：新建未确认 */
    DRAFT("DRAFT", "草稿"),

    /** 已确认 */
    CONFIRMED("CONFIRMED", "已确认"),

    /** 部分出货 */
    PARTIAL_SHIPPED("PARTIAL_SHIPPED", "部分出货"),

    /** 已完成：所有有效明细未出货数为 0 */
    COMPLETED("COMPLETED", "已完成"),

    /** 已取消 */
    CANCELED("CANCELED", "已取消"),
    ;

    /** 存库编码 */
    private final String code;

    /** 界面标签 */
    private final String label;

    /**
     * 按存库编码解析枚举（无法解析时返回 null，由调用方决定降级策略）。
     *
     * @param code 存库编码
     * @return 枚举，未匹配返回 null
     */
    public static OrderStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }

    /**
     * 是否为终态（终态不再参与自动流转）。
     *
     * @return true 表示已取消
     */
    public boolean isTerminal() {
        return this == CANCELED;
    }
}
