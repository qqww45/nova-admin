package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单分页查询条件。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "OrderPageQuery", description = "订单分页查询条件")
public class OrderPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：订单号 / 客户订单号 */
    @Schema(description = "关键字：订单号、客户订单号")
    private String keyword;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private String customerId;

    /** 订单状态编码 */
    @Schema(description = "订单状态：DRAFT/CONFIRMED/PARTIAL_SHIPPED/COMPLETED/CANCELED")
    private String status;

    /** 业务员 ID */
    @Schema(description = "业务员ID")
    private String salesmanId;

    /** 下单时间起 */
    @Schema(description = "下单时间起", example = "2026-01-01 00:00:00")
    private LocalDateTime beginDate;

    /** 下单时间止 */
    @Schema(description = "下单时间止", example = "2026-12-31 23:59:59")
    private LocalDateTime endDate;

    /** 仅看未出货完成的订单 */
    @Schema(description = "仅看未出货完成的订单")
    private Boolean onlyOpen;
}
