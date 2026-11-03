package com.example.admin.interfaces.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户应收速览行（全期间余额，实时计算不落库）。
 *
 * <p>口径与对账单完全一致（{@code StatementGenerator} 单一实现）：
 * 期末应收 = 期初 + 累计出货 − 退货 − 累计收款。业务员催款前先看这张表：
 * 谁欠得多、欠多久（最近一次出货/收款时间），催款优先级不再凭记忆。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
public class ReceivableSummaryResp {

    /** 客户 ID（ID 一律字符串，防前端精度丢失） */
    private String customerId;

    /** 客户名称 */
    private String customerName;

    /** 期初应收 */
    private BigDecimal openingBalance;

    /** 累计出货（不含退货，全期间） */
    private BigDecimal shippedAmount;

    /** 累计退货 */
    private BigDecimal returnedAmount;

    /** 累计收款 */
    private BigDecimal receivedAmount;

    /** 期末应收余额 */
    private BigDecimal endingBalance;

    /** 最近一次出货时间 */
    private LocalDateTime lastShipmentDate;

    /** 最近一次收款时间 */
    private LocalDateTime lastReceiptDate;
}
