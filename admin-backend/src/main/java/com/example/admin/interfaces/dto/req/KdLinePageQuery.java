package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 金蝶出库分录数据分页查询条件（排查页）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "KdLinePageQuery", description = "金蝶出库分录数据分页查询")
public class KdLinePageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账套 ID，空表示全部")
    private String acctId;

    @Schema(description = "金蝶出库单 FID（精确定位某张单的全部分录）")
    private String outboundFid;

    @Schema(description = "关键字：物料编码 / 物料名称 / 源单号 / 客户PO")
    private String keyword;

    @Schema(description = "是否失效：0 正常 / 1 已失效，空表示全部")
    private Integer invalid;

    @Schema(description = "最近上报批次号")
    private String batchNo;

    @Schema(description = "同步日期起 yyyy-MM-dd（含）", example = "2026-10-01")
    private String syncDateFrom;

    @Schema(description = "同步日期止 yyyy-MM-dd（含）", example = "2026-10-31")
    private String syncDateTo;

    @Schema(description = "数据归属人（仅管理员可指定，业务员强制为本人）")
    private String ownerUserId;
}
