package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单行出货数据（列顺序与用户现有 xlsx 台账一致）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ShipmentRowReq", description = "单行出货数据")
public class ShipmentRowReq {

    /** 出货日期（为空则默认今天） */
    @Schema(description = "出货日期")
    private LocalDateTime shipDate;

    /** 加工单号 */
    @Schema(description = "加工单号")
    private String processOrderNo;

    /** 业务员 ID */
    @Schema(description = "业务员ID")
    private String salesmanId;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private String customerId;

    /** 客户订单号（用于匹配订单明细） */
    @Schema(description = "客户订单号")
    private String customerOrderNo;

    /** 客户物料号（匹配明细的优先键） */
    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    /** 型号 */
    @NotBlank(message = "型号不能为空")
    @Schema(description = "型号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String model;

    /** 出货数量 */
    @Schema(description = "出货数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal qty;

    /** 料号 */
    @Schema(description = "料号")
    private String materialNo;

    /** 规格型号 */
    @Schema(description = "规格型号")
    private String spec;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 直接指定关联的订单明细 ID（从订单明细"生成出货行"时带上，可跳过自动匹配） */
    @Schema(description = "订单明细ID，可为空（自动匹配）")
    private String orderItemId;
}
