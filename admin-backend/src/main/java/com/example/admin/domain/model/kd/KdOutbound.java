package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金蝶销售出库单头（按业务员隔离）。
 *
 * <p>幂等键：{@code (ownerUserId, acctId, kdFid)}。{@code amountCents}
 * 由分录汇总得出，客户端上报的头金额仅作参考，落库以服务端重算为准。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdOutbound {

    /** 主键 */
    private Long id;

    /** 数据归属人 */
    private Long ownerUserId;

    /** 账套 */
    private String acctId;

    /** 金蝶 FID */
    private Long kdFid;

    /** 单据编号 */
    private String billNo;

    /** 业务日期 */
    private LocalDateTime bizDate;

    /** 金蝶客户内码 */
    private Long customerFid;

    /** 客户名称 */
    private String customerName;

    /** 单据状态 FDocumentStatus */
    private String docStatus;

    /** 作废状态 FCancelStatus */
    private String cancelStatus;

    /** 是否作废：1 是 0 否（派生） */
    private Integer cancelled;

    /** 表头金额（分） */
    private Long amountCents;

    /** 备注 */
    private String remark;

    /** 金蝶修改时间 */
    private LocalDateTime kdModifyTime;

    /** 是否失效：1 是 0 否（全量批次差集标记） */
    private Integer invalid;

    /** 最近上报批次 */
    private String batchNo;

    /** 最近同步时间 */
    private LocalDateTime syncAt;

    /**
     * 由作废状态派生 cancelled 标记。
     *
     * @param cancelStatus FCancelStatus（B = 已作废）
     * @return 1 / 0
     */
    public static int cancelledOf(String cancelStatus) {
        return cancelStatus != null && "B".equalsIgnoreCase(cancelStatus.trim()) ? 1 : 0;
    }
}
