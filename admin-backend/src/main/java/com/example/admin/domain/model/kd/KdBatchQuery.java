package com.example.admin.domain.model.kd;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 同步批次查询条件。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KdBatchQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 实体：customer / outbound / outbound_line / ALL */
    private String entity;

    /** 状态：RUNNING / SUCCESS / FAILED */
    private String status;

    /** 账套 */
    private String acctId;

    /** 关键字：批次号 / 结果信息 */
    private String keyword;

    /**
     * 数据归属人（服务端注入，客户端不可传）。
     *
     * <p>非管理员固定为当前登录用户；管理员为 null 表示不过滤。
     */
    private Long ownerUserId;
}
