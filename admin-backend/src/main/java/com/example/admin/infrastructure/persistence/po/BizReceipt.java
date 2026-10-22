package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款登记持久化对象（对应 biz_receipt）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_receipt")
@Schema(name = "BizReceipt", description = "收款登记")
public class BizReceipt extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 收款单号（唯一，生成后只读） */
    @Schema(description = "收款单号")
    private String receiptNo;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private Long customerId;

    /** 收款日期 */
    @Schema(description = "收款日期")
    private LocalDateTime receiptDate;

    /** 收款金额（>0） */
    @Schema(description = "收款金额")
    private BigDecimal amount;

    /** 币种 */
    @Schema(description = "币种")
    private String currency;

    /** 结算方式（字典 settlement_method） */
    @Schema(description = "结算方式")
    private String settleMethod;

    /** 对应发票号 */
    @Schema(description = "对应发票号")
    private String invoiceNo;

    /** 已分配到对账单的金额 */
    @Schema(description = "已分配金额")
    private BigDecimal allocatedAmount;

    /** 状态：NORMAL / VOID */
    @Schema(description = "状态：NORMAL正常 VOID已作废")
    private String status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 乐观锁版本号 */
    @Version
    @Schema(description = "版本号")
    private Integer version;
}
