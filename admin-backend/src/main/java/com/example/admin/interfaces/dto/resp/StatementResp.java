package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账单头响应（含明细）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "StatementResp", description = "对账单")
public class StatementResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "对账单号")
    private String stmtNo;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "对账期间起")
    private LocalDateTime startDate;

    @Schema(description = "对账期间止")
    private LocalDateTime endDate;

    @Schema(description = "期初应收")
    private BigDecimal openingBalance;

    @Schema(description = "本期出货金额")
    private BigDecimal shippedAmount;

    @Schema(description = "本期退货金额")
    private BigDecimal returnedAmount;

    @Schema(description = "本期收款金额")
    private BigDecimal receivedAmount;

    @Schema(description = "期末应收")
    private BigDecimal endingBalance;

    @Schema(description = "状态：DRAFT草稿 ISSUED已签发 CONFIRMED已确认 DIFFERENT有差异")
    private String status;

    @Schema(description = "状态标签")
    private String statusLabel;

    @Schema(description = "确认日期")
    private LocalDateTime confirmDate;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "明细行")
    private List<StatementLineResp> lines;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
