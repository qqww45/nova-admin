package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户出货汇总（M5 报表）。
 *
 * <p>财务与跟单最常问的三个问题，一行回答完：
 * 这个客户下了多少、出了多少、还欠多少、值多少钱。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerSummaryResp", description = "客户出货汇总")
public class CustomerSummaryResp {

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "订单数")
    private int orderCount;

    @Schema(description = "明细行数")
    private int itemCount;

    @Schema(description = "下单总量")
    private BigDecimal qtyOrdered = BigDecimal.ZERO;

    @Schema(description = "已出货量")
    private BigDecimal qtyShipped = BigDecimal.ZERO;

    @Schema(description = "未出货量")
    private BigDecimal qtyOpen = BigDecimal.ZERO;

    @Schema(description = "价税合计")
    private BigDecimal amountTaxIncluded = BigDecimal.ZERO;
}
