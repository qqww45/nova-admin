package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 对账单明细（单个客户 + 期间）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdStatementDetailResp", description = "对账单明细")
public class KdStatementDetailResp {

    @Schema(description = "账套 ID")
    private String acctId;

    @Schema(description = "金蝶客户内码")
    private String customerFid;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "查询起日 yyyy-MM-dd")
    private String dateFrom;

    @Schema(description = "查询止日 yyyy-MM-dd")
    private String dateTo;

    @Schema(description = "出库单数")
    private Integer billCount;

    @Schema(description = "分录行数")
    private Integer lineCount;

    @Schema(description = "合计金额（元）")
    private BigDecimal totalAmount;

    @Schema(description = "单据明细")
    private List<KdStatementBillResp> bills = new ArrayList<>();
}
