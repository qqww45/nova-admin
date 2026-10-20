package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 出货分页查询条件。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "ShipmentPageQuery", description = "出货分页查询条件")
public class ShipmentPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：加工单号 / 客户订单号 / 型号 / 料号 */
    @Schema(description = "关键字：加工单号、客户订单号、型号、料号")
    private String keyword;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private String customerId;

    /** 业务员 ID */
    @Schema(description = "业务员ID")
    private String salesmanId;

    /** 加工单号 */
    @Schema(description = "加工单号")
    private String processOrderNo;

    /** 客户订单号 */
    @Schema(description = "客户订单号")
    private String customerOrderNo;

    /** 出货日期起 */
    @Schema(description = "出货日期起", example = "2026-01-01 00:00:00")
    private LocalDateTime beginDate;

    /** 出货日期止 */
    @Schema(description = "出货日期止", example = "2026-12-31 23:59:59")
    private LocalDateTime endDate;

    /** 状态：NORMAL / VOID */
    @Schema(description = "状态：NORMAL正常 VOID已作废")
    private String status;

    /** 只看无源出货（异常数据池） */
    @Schema(description = "只看无源出货")
    private Boolean onlyOrphan;
}
