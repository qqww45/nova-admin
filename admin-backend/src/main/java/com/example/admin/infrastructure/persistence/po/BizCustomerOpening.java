package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户期初应收持久化对象（对应 biz_customer_opening）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_customer_opening")
@Schema(name = "BizCustomerOpening", description = "客户期初应收")
public class BizCustomerOpening extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private Long customerId;

    /** 期初基准日 */
    @Schema(description = "期初基准日")
    private LocalDateTime asOfDate;

    /** 期初应收 */
    @Schema(description = "期初应收")
    private BigDecimal openingBalance;

    /** 币种 */
    @Schema(description = "币种")
    private String currency;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 乐观锁版本号 */
    @Version
    @Schema(description = "版本号")
    private Integer version;
}
