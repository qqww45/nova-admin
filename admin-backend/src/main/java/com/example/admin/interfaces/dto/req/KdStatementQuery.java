package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对账单查询参数。
 *
 * <p>日期为空时默认取最近 90 天（含今天）：金蝶历史数据动辄几十万行，
 * "什么都不填就全表扫"在 SQLite / MySQL 上都会拖垮接口。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "KdStatementQuery", description = "对账单查询参数")
public class KdStatementQuery extends PageQuery {

    @Schema(description = "账套 ID，空表示全部")
    private String acctId;

    @Schema(description = "金蝶客户内码（明细/导出时传）")
    private String customerFid;

    @Schema(description = "业务日期起 yyyy-MM-dd", example = "2026-10-01")
    private String dateFrom;

    @Schema(description = "业务日期止 yyyy-MM-dd（含当天）", example = "2026-10-31")
    private String dateTo;

    @Schema(description = "客户名称关键字")
    private String keyword;
}
