package com.example.admin.domain.model.order;

import com.example.admin.domain.service.AmountCalculator;
import com.example.admin.domain.service.OrderStatusPolicy;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单头聚合根（需求 3.4）。
 *
 * <p>订单号生成后只读；状态由 {@link OrderStatusPolicy} 在出货 / 作废后重算，
 * 前端不得直接修改。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class SalesOrder {

    /** 主键 */
    private Long id;

    /** 订单号（唯一，生成后只读） */
    private String orderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称（展示用，非库字段） */
    private String customerName;

    /** 客户订单号（客户 PO 号） */
    private String customerOrderNo;

    /** 客户要求 */
    private String requirement;

    /** 主板标签 */
    private String mainboardLabel;

    /** 外箱标签 */
    private String cartonLabel;

    /** 业务员 ID */
    private Long salesmanId;

    /** 业务员姓名（展示用，非库字段） */
    private String salesmanName;

    /** 下单时间 */
    private LocalDateTime orderDate;

    /** 预计交货时间（非必填） */
    private LocalDateTime expectedDeliveryDate;

    /** 订单状态 */
    private OrderStatus status;

    /** 币种（默认 CNY） */
    private String currency;

    /** 结算方式 */
    private String settlementMethod;

    /** 交货方式 */
    private String deliveryMethod;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 明细列表 */
    private List<SalesOrderItem> items = new ArrayList<>();

    /** 是否已对账（全部明细已对账，展示用，非库字段） */
    private Boolean allReconciled;

    /**
     * 重算订单状态（出货 / 作废后必须调用）。
     */
    public void refreshStatus() {
        this.status = OrderStatusPolicy.recalculate(this.status, this.items);
    }

    /**
     * 价税合计（全部有效明细求和）。
     *
     * @return 订单价税合计
     */
    public BigDecimal totalAmountTaxIncluded() {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderItem item : safeItems()) {
            if (item.getStatus() == ItemStatus.CANCELED) {
                continue;
            }
            total = total.add(AmountCalculator.nz(item.getAmountTaxIncluded()));
        }
        return total.setScale(AmountCalculator.MONEY_SCALE, AmountCalculator.ROUNDING);
    }

    /**
     * 下单总量（全部有效明细）。
     *
     * @return 下单数量合计
     */
    public BigDecimal totalQtyOrdered() {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderItem item : safeItems()) {
            if (item.getStatus() == ItemStatus.CANCELED) {
                continue;
            }
            total = total.add(AmountCalculator.nz(item.getQtyOrdered()));
        }
        return total;
    }

    /**
     * 已出货总量（全部有效明细）。
     *
     * @return 出货数量合计
     */
    public BigDecimal totalQtyShipped() {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderItem item : safeItems()) {
            if (item.getStatus() == ItemStatus.CANCELED) {
                continue;
            }
            total = total.add(AmountCalculator.nz(item.getQtyShipped()));
        }
        return total;
    }

    /**
     * 是否允许取消（须先作废其下全部出货）。
     *
     * @return true 表示可以取消
     */
    public boolean canCancel() {
        return OrderStatusPolicy.canCancel(this.items);
    }

    private List<SalesOrderItem> safeItems() {
        return this.items == null ? List.of() : this.items;
    }
}
