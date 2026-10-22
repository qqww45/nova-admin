package com.example.admin.domain.model.reconciliation;

import com.example.admin.domain.model.reconciliation.StatementStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对账单（签发快照，含四要素汇总）。明细见 {@link StatementLine}。
 *
 * <p>恒等式：期末应收 = 期初 + 出货 − 退货/退厂 − 收款。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
public class Statement {

    /** 主键 */
    private Long id;

    /** 对账单号（生成后只读） */
    private String stmtNo;

    /** 客户 ID */
    private Long customerId;

    /** 对账期间起 */
    private LocalDateTime startDate;

    /** 对账期间止 */
    private LocalDateTime endDate;

    /** 期初应收 */
    private BigDecimal openingBalance;

    /** 本期出货（正金额合计） */
    private BigDecimal shippedAmount;

    /** 本期退货/退厂（正金额合计，展示用；计算取负） */
    private BigDecimal returnedAmount;

    /** 本期收款（正金额合计） */
    private BigDecimal receivedAmount;

    /** 期末应收 */
    private BigDecimal endingBalance;

    /** 状态 */
    private StatementStatus status;

    /** 客户确认/回签日 */
    private LocalDateTime confirmDate;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer version;

    /** 明细行 */
    private List<StatementLine> lines = new ArrayList<>();

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 签发：草稿 → 已签发。
     */
    public void issue() {
        this.status = StatementStatus.ISSUED;
    }

    /**
     * 客户确认（回签，无误）。
     */
    public void confirm() {
        this.status = StatementStatus.CONFIRMED;
        this.confirmDate = LocalDateTime.now();
    }

    /**
     * 标记差异（客户提出差异，需跟进）。
     */
    public void markDifferent() {
        this.status = StatementStatus.DIFFERENT;
        this.confirmDate = LocalDateTime.now();
    }

    /**
     * 是否可签发（仅草稿可签发）。
     *
     * @return true 表示可签发
     */
    public boolean isDraft() {
        return status == StatementStatus.DRAFT;
    }
}
