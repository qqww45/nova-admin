package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款记录响应。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "ReceiptResp", description = "收款记录")
public class ReceiptResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "收款单号")
    private String receiptNo;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "收款日期")
    private LocalDateTime receiptDate;

    @Schema(description = "收款金额")
    private BigDecimal amount;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "结算方式")
    private String settleMethod;

    @Schema(description = "结算方式标签")
    private String settleMethodLabel;

    @Schema(description = "对应发票号")
    private String invoiceNo;

    @Schema(description = "已分配金额")
    private BigDecimal allocatedAmount;

    @Schema(description = "状态：NORMAL正常 VOID已作废")
    private String status;

    @Schema(description = "状态标签")
    private String statusLabel;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
