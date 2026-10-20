package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出货记录持久化对象（对应 biz_shipment）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_shipment")
@Schema(name = "BizShipment", description = "出货记录")
public class BizShipment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 出货日期 */
    @Schema(description = "出货日期")
    private LocalDateTime shipDate;

    /** 加工单号 */
    @Schema(description = "加工单号")
    private String processOrderNo;

    /** 业务员 ID */
    @Schema(description = "业务员ID")
    private Long salesmanId;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private Long customerId;

    /** 客户订单号 */
    @Schema(description = "客户订单号")
    private String customerOrderNo;

    /** 客户物料号 */
    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    /** 型号 */
    @Schema(description = "型号")
    private String model;

    /** 料号 */
    @Schema(description = "料号")
    private String materialNo;

    /** 规格型号 */
    @Schema(description = "规格型号")
    private String spec;

    /** 出货数量 */
    @Schema(description = "出货数量")
    private BigDecimal qty;

    /** 关联订单明细 ID（为空 = 无源出货） */
    @Schema(description = "关联订单明细ID")
    private Long orderItemId;

    /** 不含税金额（快照） */
    @Schema(description = "不含税金额")
    private BigDecimal amount;

    /** 税额（快照） */
    @Schema(description = "税额")
    private BigDecimal taxAmount;

    /** 价税合计（快照） */
    @Schema(description = "价税合计")
    private BigDecimal amountTaxIncluded;

    /** 状态：NORMAL / VOID */
    @Schema(description = "状态：NORMAL正常 VOID已作废")
    private String status;

    /** 来源：MANUAL / IMPORT / PASTE */
    @Schema(description = "来源：MANUAL手工 IMPORT导入 PASTE粘贴")
    private String source;

    /** 导入批次 ID */
    @Schema(description = "导入批次ID")
    private Long importBatchId;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
