package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细保存请求。
 *
 * <p>金额与未出货数由服务端计算后返回，前端传来的同名字段一律忽略，
 * 避免"前端算错、后端照单全收"。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "OrderItemSaveReq", description = "订单明细保存请求")
public class OrderItemSaveReq {

    /** 主键（修改时必填） */
    @Schema(description = "主键，新增时为空")
    private String id;

    /** 行号 */
    @Schema(description = "行号，新增时按顺序自动生成")
    private Integer lineNo;

    /** 型号 */
    @NotBlank(message = "型号不能为空")
    @Schema(description = "型号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String model;

    /** 物料编号 */
    @Schema(description = "物料编号（我方料号）")
    private String materialNo;

    /** 客户物料号 */
    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    /** 规格型号 */
    @Schema(description = "规格型号")
    private String spec;

    /** 下单数量 */
    @Schema(description = "下单数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal qtyOrdered;

    /** 税率（0.13 = 13%，空或 0 表示不含税） */
    @Schema(description = "税率，0.13 表示 13%")
    private BigDecimal taxRate;

    /** 单价 */
    @Schema(description = "单价", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal unitPrice;

    /** 对账日期 */
    @Schema(description = "对账日期")
    private LocalDateTime reconcileDate;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
