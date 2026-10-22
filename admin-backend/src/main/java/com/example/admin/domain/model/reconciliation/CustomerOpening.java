package com.example.admin.domain.model.reconciliation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户期初应收余额（对账周期开始前的历史欠款）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
public class CustomerOpening {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 期初基准日 */
    private LocalDateTime asOfDate;

    /** 期初应收 */
    private BigDecimal openingBalance;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
