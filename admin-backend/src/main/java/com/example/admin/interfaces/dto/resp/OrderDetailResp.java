package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情响应（头 + 明细）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "OrderDetailResp", description = "订单详情")
public class OrderDetailResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "客户订单号")
    private String customerOrderNo;

    @Schema(description = "客户要求")
    private String requirement;

    @Schema(description = "主板标签")
    private String mainboardLabel;

    @Schema(description = "外箱标签")
    private String cartonLabel;

    @Schema(description = "业务员ID")
    private String salesmanId;

    @Schema(description = "业务员姓名")
    private String salesmanName;

    @Schema(description = "下单时间")
    private LocalDateTime orderDate;

    @Schema(description = "预计交货时间")
    private LocalDateTime expectedDeliveryDate;

    @Schema(description = "状态编码")
    private String status;

    @Schema(description = "状态标签")
    private String statusLabel;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "结算方式")
    private String settlementMethod;

    @Schema(description = "交货方式")
    private String deliveryMethod;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "明细")
    private List<OrderItemResp> items;

    @Schema(description = "价税合计")
    private BigDecimal amountTaxIncluded;

    @Schema(description = "下单总量")
    private BigDecimal totalQtyOrdered;

    @Schema(description = "已出货总量")
    private BigDecimal totalQtyShipped;
}
