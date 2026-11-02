package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 对账单汇总行（客户 + 期间）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdStatementSummaryResp", description = "对账单汇总")
public class KdStatementSummaryResp {

    @Schema(description = "账套 ID")
    private String acctId;

    @Schema(description = "金蝶客户内码")
    private String customerFid;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "出库单数")
    private Integer billCount;

    @Schema(description = "出库金额（元）")
    private BigDecimal totalAmount;

    @Schema(description = "最早业务日期 yyyy-MM-dd")
    private String firstDate;

    @Schema(description = "最晚业务日期 yyyy-MM-dd")
    private String lastDate;
}
