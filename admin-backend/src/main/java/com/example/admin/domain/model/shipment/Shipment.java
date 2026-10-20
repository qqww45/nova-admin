package com.example.admin.domain.model.shipment;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.service.AmountCalculator;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出货记录（需求 3.6）。
 *
 * <p>两条铁律：
 * <ul>
 *     <li>禁止物理删除，只允许作废（{@link #void_(String)}）；</li>
 *     <li>幂等键 = 加工单号 + 客户订单号 + 料号 + 出货日期 + 数量，重复导入不产生重复数据。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class Shipment {

    /** 主键 */
    private Long id;

    /** 出货日期 */
    private LocalDateTime shipDate;

    /** 加工单号（内部生产/加工流转单号，重要溯源键） */
    private String processOrderNo;

    /** 业务员 ID */
    private Long salesmanId;

    /** 业务员姓名（展示用，非库字段） */
    private String salesmanName;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称（展示用，非库字段） */
    private String customerName;

    /** 客户订单号 */
    private String customerOrderNo;

    /** 客户物料号 */
    private String customerMaterialNo;

    /** 型号 */
    private String model;

    /** 料号（我方物料编号） */
    private String materialNo;

    /** 规格型号 */
    private String spec;

    /** 出货数量（>0） */
    private BigDecimal qty;

    /** 关联订单明细 ID（为空 = 无源出货，进异常池） */
    private Long orderItemId;

    /** 关联订单号（展示用，非库字段） */
    private String orderNo;

    /** 不含税金额（快照） */
    private BigDecimal amount;

    /** 税额（快照） */
    private BigDecimal taxAmount;

    /** 价税合计（快照） */
    private BigDecimal amountTaxIncluded;

    /** 状态 */
    private ShipmentStatus status;

    /** 来源 */
    private ShipmentSource source;

    /** 导入批次 ID */
    private Long importBatchId;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 按明细口径写入本次出货的金额快照。
     *
     * @param unitPrice 明细单价
     * @param taxRate   明细税率
     */
    public void snapshotAmount(BigDecimal unitPrice, BigDecimal taxRate) {
        AmountCalculator.Amounts amounts = AmountCalculator.forShipment(unitPrice, this.qty, taxRate);
        this.amount = amounts.amountExTax();
        this.taxAmount = amounts.taxAmount();
        this.amountTaxIncluded = amounts.amountTaxIncluded();
    }

    /**
     * 作废（红冲）：只改状态，回写扣减由应用层在同一事务内完成。
     *
     * @param reason 作废原因（敏感操作，必填）
     */
    public void void_(String reason) {
        if (this.status == ShipmentStatus.VOID) {
            return;
        }
        this.status = ShipmentStatus.VOID;
        if (reason != null && !reason.isBlank()) {
            this.remark = (this.remark == null ? "" : this.remark + "；") + "作废原因：" + reason;
        }
    }

    /**
     * 是否已作废。
     *
     * @return true 表示已作废
     */
    public boolean isVoid() {
        return status == ShipmentStatus.VOID;
    }

    /**
     * 补挂订单明细（异常数据池的修复动作）。
     *
     * <p>为什么不直接允许改 orderItemId 就完事：无源出货一旦挂上明细，
     * 明细的出货数就要跟着涨，否则会出现"出货有记录、明细却显示未出货"的对不上账。
     * 因此这里只负责登记关联关系，**数量的回写由应用层在同一事务内调用**
     * {@code SalesOrderItem.applyShipment}，与正常出货走同一条路。
     *
     * @param itemId 订单明细 ID
     * @throws com.example.admin.common.exception.BusinessException 已作废的出货不允许补挂
     */
    public void linkTo(Long itemId) {
        if (itemId == null) {
            throw new BusinessException("请选择要关联的订单明细");
        }
        if (isVoid()) {
            throw new BusinessException("已作废的出货不支持补挂订单");
        }
        this.orderItemId = itemId;
    }

    /**
     * 是否为无源出货（未匹配到订单明细，进异常数据池）。
     *
     * @return true 表示无源
     */
    public boolean isOrphan() {
        return orderItemId == null;
    }

    /**
     * 构造幂等键（用于 upsert / 跳过判重）。
     *
     * @return 幂等键字符串
     */
    public String idempotentKey() {
        return idempotentKey(processOrderNo, customerOrderNo, materialNo, shipDate, qty);
    }

    /**
     * 构造幂等键（静态版本，导入预览时未落库也能用）。
     *
     * @param processOrderNo 加工单号
     * @param customerOrderNo 客户订单号
     * @param materialNo     料号
     * @param shipDate       出货日期
     * @param qty            数量
     * @return 幂等键字符串
     */
    public static String idempotentKey(String processOrderNo, String customerOrderNo, String materialNo,
                                       LocalDateTime shipDate, BigDecimal qty) {
        String date = shipDate == null ? "" : shipDate.toLocalDate().toString();
        return String.join("|",
                nullToEmpty(processOrderNo),
                nullToEmpty(customerOrderNo),
                nullToEmpty(materialNo),
                date,
                qty == null ? "" : qty.stripTrailingZeros().toPlainString());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
