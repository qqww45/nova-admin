package com.example.admin.domain.model.order;

import com.example.admin.domain.service.AmountCalculator;
import com.example.admin.domain.service.QuantityPolicy;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细（出货与对账的最小单位，需求 3.5）。
 *
 * <p>充血点：金额三元组与未出货数由聚合自身计算（{@link #recalculate()}），
 * 调用方无法绕过规则直接 set，保证界面录入与 Excel 导入口径一致。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class SalesOrderItem {

    /** 主键 */
    private Long id;

    /** 订单头 ID */
    private Long orderId;

    /** 行号（展示顺序，从 1 开始） */
    private Integer lineNo;

    /** 型号 */
    private String model;

    /** 物料编号（我方料号） */
    private String materialNo;

    /** 客户物料号 */
    private String customerMaterialNo;

    /** 规格型号 */
    private String spec;

    /** 下单数量（>0） */
    private BigDecimal qtyOrdered;

    /** 出货数（累计，由出货记录回写） */
    private BigDecimal qtyShipped;

    /** 未出货数（派生 = 下单数量 − 出货数，禁止录入） */
    private BigDecimal qtyOpen;

    /** 税率（0.13 = 13%；空或 0 表示单价为不含税） */
    private BigDecimal taxRate;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 派生：1 含税单价 0 不含税单价 */
    private Integer priceIsTaxIncluded;

    /** 不含税金额 */
    private BigDecimal amountExTax;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 价税合计 */
    private BigDecimal amountTaxIncluded;

    /** 对账日期 */
    private LocalDateTime reconcileDate;

    /** 对账状态 */
    private ReconcileStatus reconcileStatus;

    /** 行状态 */
    private ItemStatus status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 重算全部派生值：未出货数、含税标记、金额三元组、对账状态。
     *
     * <p>保存前、出货回写后都必须调用，避免出现"金额与单价对不上"的脏数据。
     */
    public void recalculate() {
        this.qtyOpen = QuantityPolicy.qtyOpen(this.qtyOrdered, this.qtyShipped);
        boolean taxIncluded = AmountCalculator.isTaxIncluded(this.taxRate);
        this.priceIsTaxIncluded = taxIncluded ? 1 : 0;
        AmountCalculator.Amounts amounts = AmountCalculator.calculate(this.unitPrice, this.qtyOrdered, this.taxRate);
        this.amountExTax = amounts.amountExTax();
        this.taxAmount = amounts.taxAmount();
        this.amountTaxIncluded = amounts.amountTaxIncluded();
        if (this.reconcileDate != null) {
            this.reconcileStatus = ReconcileStatus.RECONCILED;
        } else if (this.reconcileStatus == null) {
            this.reconcileStatus = ReconcileStatus.UNRECONCILED;
        }
        if (this.status == null) {
            this.status = ItemStatus.NORMAL;
        }
    }

    /**
     * 登记出货：校验数量并累加出货数（实际需求由 ShipmentAppService 在同一事务内落库）。
     *
     * @param qty 本次出货数量
     */
    public void applyShipment(BigDecimal qty) {
        QuantityPolicy.validateShipQty(qty, this.qtyOpen, "型号 " + this.model);
        this.qtyShipped = QuantityPolicy.addShipped(this.qtyShipped, qty);
        recalculate();
    }

    /**
     * 作废出货：扣减出货数。
     *
     * @param qty 被作废的出货数量
     */
    public void revertShipment(BigDecimal qty) {
        this.qtyShipped = QuantityPolicy.subtractShipped(this.qtyShipped, qty);
        recalculate();
    }

    /**
     * 填写对账日期（填写后即置为已对账）。
     *
     * @param date 对账日期（传 null 表示撤销对账）
     */
    public void reconcile(LocalDateTime date) {
        this.reconcileDate = date;
        this.reconcileStatus = date == null ? ReconcileStatus.UNRECONCILED : ReconcileStatus.RECONCILED;
    }

    /**
     * 是否已完成出货。
     *
     * @return 未出货数为 0 返回 true
     */
    public boolean isFullyShipped() {
        return AmountCalculator.nz(this.qtyOpen).compareTo(BigDecimal.ZERO) <= 0;
    }
}
