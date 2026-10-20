package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单保存请求（头 + 明细一起提交，整单保存）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "OrderSaveReq", description = "订单保存请求")
public class OrderSaveReq {

    /** 主键（修改时必填） */
    @Schema(description = "主键，新增时为空")
    private String id;

    /** 订单号（新增时为空则由服务端生成，生成后只读） */
    @Schema(description = "订单号，新增留空由系统生成")
    private String orderNo;

    /** 客户 ID */
    @NotBlank(message = "客户不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    /** 客户订单号 */
    @Schema(description = "客户订单号")
    private String customerOrderNo;

    /** 客户要求 */
    @Schema(description = "客户要求")
    private String requirement;

    /** 主板标签 */
    @Schema(description = "主板标签")
    private String mainboardLabel;

    /** 外箱标签 */
    @Schema(description = "外箱标签")
    private String cartonLabel;

    /** 业务员 ID */
    @NotBlank(message = "业务员不能为空")
    @Schema(description = "业务员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String salesmanId;

    /** 下单时间 */
    @Schema(description = "下单时间")
    private LocalDateTime orderDate;

    /** 预计交货时间（非必填） */
    @Schema(description = "预计交货时间，非必填")
    private LocalDateTime expectedDeliveryDate;

    /** 币种 */
    @Schema(description = "币种，默认 CNY")
    private String currency;

    /** 结算方式 */
    @Schema(description = "结算方式")
    private String settlementMethod;

    /** 交货方式 */
    @Schema(description = "交货方式")
    private String deliveryMethod;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 明细行 */
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    @Schema(description = "订单明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<OrderItemSaveReq> items;
}
