package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 金蝶出库单数据分页查询条件（排查页）。
 *
 * <p>与对账单查询的差异：可按作废 / 失效 / 批次过滤，
 * 且不限制查询窗口——排查页要能看历史任意区间。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "KdOutboundPageQuery", description = "金蝶出库单数据分页查询")
public class KdOutboundPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账套 ID，空表示全部")
    private String acctId;

    @Schema(description = "关键字：单据编号 / 客户名称")
    private String keyword;

    @Schema(description = "业务日期起 yyyy-MM-dd（含）", example = "2026-10-01")
    private String dateFrom;

    @Schema(description = "业务日期止 yyyy-MM-dd（含）", example = "2026-10-31")
    private String dateTo;

    @Schema(description = "单据状态 FDocumentStatus：A 创建 / B 审核中 / C 已审核 / D 重新审核")
    private String docStatus;

    @Schema(description = "是否作废：0 正常 / 1 已作废，空表示全部")
    private Integer cancelled;

    @Schema(description = "是否失效（全量差集标记）：0 正常 / 1 已失效，空表示全部")
    private Integer invalid;

    @Schema(description = "最近上报批次号")
    private String batchNo;

    @Schema(description = "数据归属人（仅管理员可指定，业务员强制为本人）")
    private String ownerUserId;
}
