package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入批次响应（历史批次列表）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ImportBatchResp", description = "导入批次")
public class ImportBatchResp {

    @Schema(description = "批次ID")
    private String id;

    @Schema(description = "导入类型")
    private String batchType;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "工作表名")
    private String sheetName;

    @Schema(description = "解析总行数")
    private Integer totalCount;

    @Schema(description = "新增条数")
    private Integer insertCount;

    @Schema(description = "更新条数")
    private Integer updateCount;

    @Schema(description = "跳过条数")
    private Integer skipCount;

    @Schema(description = "失败条数")
    private Integer failCount;

    @Schema(description = "警告条数")
    private Integer warnCount;

    @Schema(description = "幂等策略")
    private String idempotent;

    @Schema(description = "错误清单文件名")
    private String errorFile;

    @Schema(description = "批次状态")
    private String status;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "导入时间")
    private LocalDateTime createTime;
}
