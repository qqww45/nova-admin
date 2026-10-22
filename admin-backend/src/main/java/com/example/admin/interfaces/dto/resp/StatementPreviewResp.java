package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 对账单预览响应（实时聚合，未落库）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "StatementPreviewResp", description = "对账单预览")
public class StatementPreviewResp {

    @Schema(description = "明细行")
    private List<StatementLineResp> lines;

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
}
