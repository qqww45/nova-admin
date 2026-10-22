package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款登记保存请求。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "ReceiptSaveReq", description = "收款登记保存请求")
public class ReceiptSaveReq {

    /** 主键（更新时必填） */
    @Schema(description = "主键ID，更新时必填")
    private String id;

    /** 客户 ID */
    @NotNull(message = "客户不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    /** 收款日期 */
    @Schema(description = "收款日期")
    private LocalDateTime receiptDate;

    /** 收款金额（>0） */
    @NotNull(message = "收款金额不能为空")
    @DecimalMin(value = "0.0001", inclusive = true, message = "收款金额必须大于 0")
    @Schema(description = "收款金额", requiredMode = Schema.RequiredMode.REQUIRED)
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

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
