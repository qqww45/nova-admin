package com.example.admin.interfaces.dto.resp;

import lombok.Data;

/**
 * 金蝶对账单数据体检响应。
 *
 * <p>回答一个问题："本期为什么对账单少数据 / 金额对不上"。
 * 三类不进对账单的单据各计一个数：无分录（退回表头金额，口径与页面不一致的第一嫌疑）、
 * 作废、已被全量差集标失效。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
public class KdStatementHealthResp {

    /** 窗口起（yyyy-MM-dd） */
    private String dateFrom;

    /** 窗口止（yyyy-MM-dd，含当天） */
    private String dateTo;

    /** 有效出库单数（未作废、未失效） */
    private Integer billCount;

    /** 其中无分录数据的单数（对账单退回表头金额） */
    private Integer noLineCount;

    /** 作废单数（cancel_status = B） */
    private Integer cancelledCount;

    /** 已失效单数（金蝶侧物理删除，由全量差集标记） */
    private Integer invalidCount;

    /** 是否全部健康（有效单都有分录，且无作废/失效） */
    public boolean isHealthy() {
        return noLineCount != null && noLineCount == 0
                && cancelledCount != null && cancelledCount == 0
                && invalidCount != null && invalidCount == 0;
    }
}
