package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 金蝶客户数据分页查询条件（排查页）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "KdCustomerPageQuery", description = "金蝶客户数据分页查询")
public class KdCustomerPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账套 ID，空表示全部")
    private String acctId;

    @Schema(description = "关键字：客户名称 / 金蝶客户编码")
    private String keyword;

    @Schema(description = "是否禁用：0 启用 / 1 禁用，空表示全部")
    private Integer disabled;

    @Schema(description = "最近上报批次号")
    private String batchNo;

    @Schema(description = "同步日期起 yyyy-MM-dd（含）", example = "2026-10-01")
    private String syncDateFrom;

    @Schema(description = "同步日期止 yyyy-MM-dd（含）", example = "2026-10-31")
    private String syncDateTo;

    @Schema(description = "数据归属人（仅管理员可指定，业务员强制为本人）")
    private String ownerUserId;
}
