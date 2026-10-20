package com.example.admin.domain.model.order;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单查询条件（领域查询对象）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：订单号 / 客户名称 / 客户订单号 */
    private String keyword;

    /** 订单号 */
    private String orderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户订单号 */
    private String customerOrderNo;

    /** 订单状态 */
    private OrderStatus status;

    /** 业务员 ID（数据权限） */
    private Long salesmanId;

    /** 下单时间起 */
    private LocalDateTime beginDate;

    /** 下单时间止 */
    private LocalDateTime endDate;

    /** 仅看未出货完成的订单（我的未出货明细等常用视图） */
    private Boolean onlyOpen;
}
