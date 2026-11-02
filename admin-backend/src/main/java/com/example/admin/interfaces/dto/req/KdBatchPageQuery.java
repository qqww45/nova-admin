package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 同步批次分页查询条件。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "KdBatchPageQuery", description = "同步批次分页查询")
public class KdBatchPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实体")
    private String entity;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "账套")
    private String acctId;

    @Schema(description = "关键字：批次号 / 结果信息")
    private String keyword;
}
