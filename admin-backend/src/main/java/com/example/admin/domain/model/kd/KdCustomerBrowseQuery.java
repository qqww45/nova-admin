package com.example.admin.domain.model.kd;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 金蝶客户（采集器上行）浏览查询条件。
 *
 * <p>区别于写侧仓储：这是只读浏览（排查"数据有没有上来、长什么样"），
 * 不过滤禁用客户——禁用与否本身就是排查信息。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KdCustomerBrowseQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 账套；空表示不限 */
    private String acctId;

    /** 关键字：客户名称 / 金蝶客户编码 */
    private String keyword;

    /** 是否禁用：null 不限 / 0 启用 / 1 禁用 */
    private Integer disabled;

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
