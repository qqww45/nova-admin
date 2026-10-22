package com.example.admin.domain.model.receipt;

import com.example.admin.domain.model.receipt.ReceiptStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款登记（客户级回款）。
 *
 * <p>v1 仅做到客户级收款，分配（"分 XXX 元"）通过 {@code allocatedAmount} 留痕，
 * 不强制绑定到具体对账单。禁止物理删除，只允许作废（{@link #void_(String)}）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
public class Receipt {

    /** 主键 */
    private Long id;

    /** 收款单号（生成后只读） */
    private String receiptNo;

    /** 客户 ID */
    private Long customerId;

    /** 收款日期 */
    private LocalDateTime receiptDate;

    /** 收款金额（>0） */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 结算方式（字典 settlement_method） */
    private String settleMethod;

    /** 对应发票号 */
    private String invoiceNo;

    /** 已分配到对账单的金额 */
    private BigDecimal allocatedAmount;

    /** 状态 */
    private ReceiptStatus status;

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

    /**
     * 作废（红冲）：只改状态，回写扣减由应用层在同一事务内完成。
     *
     * @param reason 作废原因（敏感操作，必填）
     */
    public void void_(String reason) {
        if (this.status == ReceiptStatus.VOID) {
            return;
        }
        this.status = ReceiptStatus.VOID;
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
        return status == ReceiptStatus.VOID;
    }
}
