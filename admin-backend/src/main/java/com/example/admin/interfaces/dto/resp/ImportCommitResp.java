package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 导入确认（第三步：确认导入）响应。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ImportCommitResp", description = "导入确认结果")
public class ImportCommitResp {

    @Schema(description = "导入批次ID，可用于回滚")
    private String batchId;

    @Schema(description = "解析总行数")
    private int total;

    @Schema(description = "新增条数")
    private int insertCount;

    @Schema(description = "更新条数")
    private int updateCount;

    @Schema(description = "跳过条数")
    private int skipCount;

    @Schema(description = "失败条数")
    private int failCount;

    @Schema(description = "警告条数")
    private int warnCount;

    @Schema(description = "逐行结果（仅失败与警告行）")
    private List<BatchLineResult> results;
}
