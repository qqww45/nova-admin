package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细持久化对象（对应 biz_sales_order_item）。
 *
 * <p>金额与税率一律 BigDecimal，对应库里的 DECIMAL 列，禁止用浮点。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_sales_order_item")
@Schema(name = "BizSalesOrderItem", description = "订单明细")
public class BizSalesOrderItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单头 ID */
    @Schema(description = "订单头ID")
    private Long orderId;

    /** 行号 */
    @Schema(description = "行号")
    private Integer lineNo;

    /** 型号 */
    @Schema(description = "型号")
    private String model;

    /** 物料编号（我方料号） */
    @Schema(description = "物料编号")
    private String materialNo;

    /** 客户物料号 */
    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    /** 规格型号 */
    @Schema(description = "规格型号")
    private String spec;

    /** 下单数量 */
    @Schema(description = "下单数量")
    private BigDecimal qtyOrdered;

    /** 出货数（累计） */
    @Schema(description = "出货数")
    private BigDecimal qtyShipped;

    /** 未出货数（派生） */
    @Schema(description = "未出货数")
    private BigDecimal qtyOpen;

    /** 税率（0.13 = 13%） */
    @Schema(description = "税率")
    private BigDecimal taxRate;

    /** 单价 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 派生：1 含税单价 0 不含税单价 */
    @Schema(description = "单价是否含税：1是 0否")
    private Integer priceIsTaxIncluded;

    /** 不含税金额 */
    @Schema(description = "不含税金额")
    private BigDecimal amountExTax;

    /** 税额 */
    @Schema(description = "税额")
    private BigDecimal taxAmount;

    /** 价税合计 */
    @Schema(description = "价税合计")
    private BigDecimal amountTaxIncluded;

    /** 对账日期 */
    @Schema(description = "对账日期")
    private LocalDateTime reconcileDate;

    /** 对账状态：UNRECONCILED / RECONCILED */
    @Schema(description = "对账状态")
    private String reconcileStatus;

    /** 行状态：NORMAL / CANCELED */
    @Schema(description = "行状态")
    private String status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 乐观锁版本号 */
    @Schema(description = "乐观锁版本号")
    @Version
    private Integer version;
}
