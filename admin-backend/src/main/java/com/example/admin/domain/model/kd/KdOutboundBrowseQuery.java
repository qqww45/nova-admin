package com.example.admin.domain.model.kd;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 金蝶出库单头（采集器上行）浏览查询条件。
 *
 * <p>与对账单口径的差异：对账单只统计有效单（未作废、未失效），
 * 浏览页必须能看到全部行——"单据作废 / 失效后为什么对不上账"正是
 * 典型排查诉求（docs/金蝶对账同步方案.md §3.3 / §12.4）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KdOutboundBrowseQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 账套；空表示不限 */
    private String acctId;

    /** 关键字：单据编号 / 客户名称 */
    private String keyword;

    /** 业务日期起（含） */
    private LocalDateTime bizFrom;

    /** 业务日期止（不含，已经 +1 天） */
    private LocalDateTime bizTo;

    /** 单据状态 FDocumentStatus：A 创建 / B 审核中 / C 已审核 / D 重新审核；空表示不限 */
    private String docStatus;

    /** 是否作废：null 不限 / 0 正常 / 1 已作废 */
    private Integer cancelled;

    /** 是否失效（全量差集标记）：null 不限 / 0 正常 / 1 已失效 */
    private Integer invalid;

    /** 最近上报批次；空表示不限 */
    private String batchNo;

    /**
     * 数据归属人（服务端注入，客户端不可传）。
     *
     * <p>非管理员固定为当前登录用户；管理员为 null 表示不过滤，
     * 也可指定某个业务员只看他的数据。
     */
    private Long ownerUserId;
}
