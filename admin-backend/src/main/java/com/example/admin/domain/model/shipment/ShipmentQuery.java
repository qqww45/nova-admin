package com.example.admin.domain.model.shipment;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 出货查询条件（领域查询对象）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShipmentQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：加工单号 / 客户订单号 / 型号 / 客户名称 */
    private String keyword;

    /** 客户 ID */
    private Long customerId;

    /** 业务员 ID（数据权限） */
    private Long salesmanId;

    /** 加工单号 */
    private String processOrderNo;

    /** 客户订单号 */
    private String customerOrderNo;

    /** 出货日期起 */
    private LocalDateTime beginDate;

    /** 出货日期止 */
    private LocalDateTime endDate;

    /** 状态 */
    private ShipmentStatus status;

    /** 来源 */
    private ShipmentSource source;

    /** 只看无源出货（异常数据池） */
    private Boolean onlyOrphan;

    /** 按订单明细查流水 */
    private Long orderItemId;
}
