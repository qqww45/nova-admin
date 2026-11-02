package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 对账单明细单据。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdStatementBillResp", description = "对账单明细单据")
public class KdStatementBillResp {

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "出库单编号")
    private String billNo;

    @Schema(description = "业务日期 yyyy-MM-dd")
    private String bizDate;

    @Schema(description = "单据状态 FDocumentStatus")
    private String docStatus;

    @Schema(description = "单据金额（元）")
    private BigDecimal amount;

    @Schema(description = "分录")
    private List<KdStatementLineResp> lines = new ArrayList<>();
}
