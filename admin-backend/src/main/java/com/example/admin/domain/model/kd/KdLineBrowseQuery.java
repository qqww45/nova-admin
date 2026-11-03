package com.example.admin.domain.model.kd;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 金蝶出库分录（采集器上行）浏览查询条件。
 *
 * <p>分录表没有业务日期，时间范围按最近同步时间（sync_at）过滤；
 * 排查"某物料 / 某个客户 PO 到底上没上来"在本页按关键字检索。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KdLineBrowseQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 账套；空表示不限 */
    private String acctId;

    /** 金蝶出库单 FID；null 表示不限（精确定位某张单的全部分录） */
    private Long outboundFid;

    /** 关键字：物料编码 / 物料名称 / 源单号 / 客户PO */
    private String keyword;

    /** 是否失效：null 不限 / 0 正常 / 1 已失效 */
    private Integer invalid;

    /** 最近上报批次；空表示不限 */
    private String batchNo;

    /** 同步时间起（含） */
    private LocalDateTime syncFrom;

    /** 同步时间止（不含） */
    private LocalDateTime syncTo;

    /**
     * 数据归属人（服务端注入，客户端不可传）。
     *
     * <p>非管理员固定为当前登录用户；管理员为 null 表示不过滤，
     * 也可指定某个业务员只看他的数据。
     */
    private Long ownerUserId;
}
