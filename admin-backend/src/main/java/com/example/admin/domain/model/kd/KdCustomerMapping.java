package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金蝶客户 ↔ 内部客户映射（两口径联动的地基）。
 *
 * <p>内部系统（{@code biz_customer}）与金蝶侧（{@code kd_customer}）是两套客户体系；
 * 映射按「归属人 + 账套 + 金蝶内码」唯一——金蝶内码只在账套内唯一。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
public class KdCustomerMapping {

    /** 匹配类型：名称标准化匹配的候选，需人工确认 */
    public static final String TYPE_AUTO_SUGGEST = "AUTO_SUGGEST";

    /** 匹配类型：业务员显式指定，视为已确认 */
    public static final String TYPE_MANUAL = "MANUAL";

    /** 主键 */
    private Long id;

    /** 数据归属人（nova 用户） */
    private Long ownerUserId;

    /** 金蝶账套 */
    private String acctId;

    /** 金蝶客户内码 FCUSTID */
    private Long kdFid;

    /** 内部客户 ID（biz_customer.id） */
    private Long bizCustomerId;

    /** 匹配类型：AUTO_SUGGEST / MANUAL */
    private String matchType;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否已确认（人工指定过）。
     *
     * @return true 表示 MANUAL
     */
    public boolean isConfirmed() {
        return TYPE_MANUAL.equalsIgnoreCase(matchType);
    }
}
