package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 金蝶客户 ↔ 内部客户映射持久化对象（对应 kd_customer_mapping）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_customer_mapping")
@Schema(name = "KdCustomerMappingPo", description = "金蝶客户与内部客户映射")
public class KdCustomerMappingPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "数据归属人")
    private Long ownerUserId;

    @Schema(description = "金蝶账套")
    private String acctId;

    @Schema(description = "金蝶客户内码 FCUSTID")
    private Long kdFid;

    @Schema(description = "内部客户 ID")
    private Long bizCustomerId;

    @Schema(description = "匹配类型：AUTO_SUGGEST / MANUAL")
    private String matchType;

    @Schema(description = "备注")
    private String remark;
}
