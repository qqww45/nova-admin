package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账单明细行响应。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "StatementLineResp", description = "对账单明细行")
public class StatementLineResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "行类型：SHIP出货 RETURN退货 RECEIVE收款")
    private String lineType;

    @Schema(description = "行类型标签")
    private String lineTypeLabel;

    @Schema(description = "业务日期")
    private LocalDateTime bizDate;

    @Schema(description = "单据号")
    private String docNo;

    @Schema(description = "客户订单号")
    private String customerOrderNo;

    @Schema(description = "物料编号")
    private String materialNo;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "规格")
    private String spec;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "数量")
    private BigDecimal qty;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "来源ID")
    private String sourceId;

    @Schema(description = "排序")
    private Integer sortNo;
}
