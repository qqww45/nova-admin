package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 收款分页查询条件。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "ReceiptPageQuery", description = "收款分页查询条件")
public class ReceiptPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：收款单号 / 发票号 */
    @Schema(description = "关键字：收款单号、发票号")
    private String keyword;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private String customerId;

    /** 收款日期起 */
    @Schema(description = "收款日期起")
    private LocalDateTime beginDate;

    /** 收款日期止 */
    @Schema(description = "收款日期止")
    private LocalDateTime endDate;

    /** 状态：NORMAL / VOID */
    @Schema(description = "状态：NORMAL正常 VOID已作废")
    private String status;
}
