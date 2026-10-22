package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 对账单分页查询条件。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "StatementPageQuery", description = "对账单分页查询条件")
public class StatementPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：对账单号 */
    @Schema(description = "关键字：对账单号")
    private String keyword;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private String customerId;

    /** 状态：DRAFT / ISSUED / CONFIRMED / DIFFERENT */
    @Schema(description = "状态")
    private String status;

    /** 对账期间起（按 end_date 过滤） */
    @Schema(description = "对账期间起")
    private LocalDateTime beginDate;

    /** 对账期间止 */
    @Schema(description = "对账期间止")
    private LocalDateTime endDate;
}
