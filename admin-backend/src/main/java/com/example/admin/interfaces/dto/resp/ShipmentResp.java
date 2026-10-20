package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出货记录响应。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ShipmentResp", description = "出货记录")
public class ShipmentResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "出货日期")
    private LocalDateTime shipDate;

    @Schema(description = "加工单号")
    private String processOrderNo;

    @Schema(description = "业务员ID")
    private String salesmanId;

    @Schema(description = "业务员姓名")
    private String salesmanName;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "客户订单号")
    private String customerOrderNo;

    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    @Schema(description = "型号")
    private String model;

    @Schema(description = "料号")
    private String materialNo;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "出货数量")
    private BigDecimal qty;

    @Schema(description = "订单明细ID，为空表示无源出货")
    private String orderItemId;

    @Schema(description = "关联订单号")
    private String orderNo;

    @Schema(description = "不含税金额")
    private BigDecimal amount;

    @Schema(description = "税额")
    private BigDecimal taxAmount;

    @Schema(description = "价税合计")
    private BigDecimal amountTaxIncluded;

    @Schema(description = "状态：NORMAL正常 VOID已作废")
    private String status;

    @Schema(description = "状态标签")
    private String statusLabel;

    @Schema(description = "来源：MANUAL手工 IMPORT导入 PASTE粘贴")
    private String source;

    @Schema(description = "来源标签")
    private String sourceLabel;

    @Schema(description = "导入批次ID")
    private String importBatchId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
