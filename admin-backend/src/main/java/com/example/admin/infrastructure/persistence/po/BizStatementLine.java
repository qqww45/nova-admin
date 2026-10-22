package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账单明细持久化对象（对应 biz_statement_line，流水快照）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@TableName("biz_statement_line")
@Schema(name = "BizStatementLine", description = "对账单明细")
public class BizStatementLine {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @Schema(description = "主键ID")
    private Long id;

    /** 对账单 ID */
    @Schema(description = "对账单ID")
    private Long statementId;

    /** 行类型：SHIP / RETURN / RECEIVE */
    @Schema(description = "行类型：SHIP出货 RETURN退货 RECEIVE收款")
    private String lineType;

    /** 业务日期 */
    @Schema(description = "业务日期")
    private LocalDateTime bizDate;

    /** 出货单号 / 收款单号 */
    @Schema(description = "单据号")
    private String docNo;

    /** 客户订单号 */
    @Schema(description = "客户订单号")
    private String customerOrderNo;

    /** 物料编号 */
    @Schema(description = "物料编号")
    private String materialNo;

    /** 物料名称 */
    @Schema(description = "物料名称")
    private String materialName;

    /** 规格 */
    @Schema(description = "规格")
    private String spec;

    /** 单位 */
    @Schema(description = "单位")
    private String unit;

    /** 数量（退货/收款方向为负） */
    @Schema(description = "数量")
    private BigDecimal qty;

    /** 单价 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 金额（退货/收款方向为负） */
    @Schema(description = "金额")
    private BigDecimal amount;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 关联 shipment.id / receipt.id */
    @Schema(description = "来源ID")
    private Long sourceId;

    /** 排序 */
    @Schema(description = "排序")
    private Integer sortNo;
}
