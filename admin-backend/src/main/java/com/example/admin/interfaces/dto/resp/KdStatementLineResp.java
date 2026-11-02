package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 对账单明细分录。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdStatementLineResp", description = "对账单明细分录")
public class KdStatementLineResp {

    @Schema(description = "分录内码")
    private String entryId;

    @Schema(description = "物料编码")
    private String materialNumber;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "数量")
    private BigDecimal qty;

    @Schema(description = "单价（元）")
    private BigDecimal price;

    @Schema(description = "金额（元）")
    private BigDecimal amount;

    @Schema(description = "源单编号")
    private String srcOrderNo;

    @Schema(description = "客户采购订单号")
    private String customerPo;

    @Schema(description = "备注")
    private String remark;
}
