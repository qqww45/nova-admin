package com.example.admin.domain.model.reconciliation;

import com.example.admin.domain.model.reconciliation.StatementLineType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账单明细行（流水快照）。
 *
 * <p>lineType 决定方向：SHIP 出货为正、RETURN 退货为负、RECEIVE 收款为负。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
public class StatementLine {

    /** 主键 */
    private Long id;

    /** 对账单 ID */
    private Long statementId;

    /** 行类型 */
    private StatementLineType lineType;

    /** 业务日期 */
    private LocalDateTime bizDate;

    /** 单据号（出货单号 / 收款单号） */
    private String docNo;

    /** 客户订单号 */
    private String customerOrderNo;

    /** 物料编号 */
    private String materialNo;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 数量（退货/收款方向为负） */
    private BigDecimal qty;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额（退货/收款方向为负） */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 关联 shipment.id / receipt.id */
    private Long sourceId;

    /** 排序 */
    private Integer sortNo;
}
