package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细响应。
 *
 * <p>金额与未出货数一律服务端计算后下发；列头"含税/不含税"由
 * {@code priceIsTaxIncluded} 驱动，前端据此动态显示单价列头。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "OrderItemResp", description = "订单明细")
public class OrderItemResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "订单ID")
    private String orderId;

    @Schema(description = "行号")
    private Integer lineNo;

    @Schema(description = "型号")
    private String model;

    @Schema(description = "物料编号")
    private String materialNo;

    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "下单数量")
    private BigDecimal qtyOrdered;

    @Schema(description = "出货数")
    private BigDecimal qtyShipped;

    @Schema(description = "未出货数")
    private BigDecimal qtyOpen;

    @Schema(description = "税率")
    private BigDecimal taxRate;

    @Schema(description = "税率标签，如 13%")
    private String taxRateLabel;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "单价是否含税：1是 0否")
    private Integer priceIsTaxIncluded;

    @Schema(description = "不含税金额")
    private BigDecimal amountExTax;

    @Schema(description = "税额")
    private BigDecimal taxAmount;

    @Schema(description = "价税合计")
    private BigDecimal amountTaxIncluded;

    @Schema(description = "对账日期")
    private LocalDateTime reconcileDate;

    @Schema(description = "对账状态")
    private String reconcileStatus;

    @Schema(description = "对账状态标签")
    private String reconcileStatusLabel;

    @Schema(description = "行状态")
    private String status;

    @Schema(description = "行状态标签")
    private String statusLabel;

    @Schema(description = "备注")
    private String remark;
}
