package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单简要信息（客户详情页"关联订单"用）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "OrderBriefResp", description = "订单简要信息")
public class OrderBriefResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "客户订单号")
    private String customerOrderNo;

    @Schema(description = "下单时间")
    private LocalDateTime orderDate;

    @Schema(description = "状态编码")
    private String status;

    @Schema(description = "状态标签")
    private String statusLabel;

    @Schema(description = "价税合计")
    private BigDecimal amountTaxIncluded;

    @Schema(description = "下单总量")
    private BigDecimal totalQtyOrdered;

    @Schema(description = "已出货总量")
    private BigDecimal totalQtyShipped;
}
