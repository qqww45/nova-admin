package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单头持久化对象（对应 biz_sales_order）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_sales_order")
@Schema(name = "BizSalesOrder", description = "订单头")
public class BizSalesOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单号（唯一，生成后只读） */
    @Schema(description = "订单号")
    private String orderNo;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private Long customerId;

    /** 客户订单号（客户 PO 号） */
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
    @Schema(description = "业务员ID")
    private Long salesmanId;

    /** 下单时间 */
    @Schema(description = "下单时间")
    private LocalDateTime orderDate;

    /** 预计交货时间（非必填） */
    @Schema(description = "预计交货时间")
    private LocalDateTime expectedDeliveryDate;

    /** 订单状态编码（见 OrderStatus） */
    @Schema(description = "订单状态：DRAFT/CONFIRMED/PARTIAL_SHIPPED/COMPLETED/CANCELED")
    private String status;

    /** 币种 */
    @Schema(description = "币种")
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

    /** 乐观锁版本号 */
    @Schema(description = "乐观锁版本号")
    @Version
    private Integer version;
}
