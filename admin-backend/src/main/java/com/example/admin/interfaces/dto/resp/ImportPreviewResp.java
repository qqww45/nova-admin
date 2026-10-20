package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 导入预览（第一步：上传解析）响应 —— 只报告不落库。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ImportPreviewResp", description = "导入预览结果")
public class ImportPreviewResp {

    @Schema(description = "导入类型")
    private String batchType;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "工作表名")
    private String sheetName;

    @Schema(description = "解析总行数")
    private int total;

    @Schema(description = "预计新增")
    private int insertCount;

    @Schema(description = "预计更新")
    private int updateCount;

    @Schema(description = "预计跳过（幂等命中）")
    private int skipCount;

    @Schema(description = "阻断级错误条数")
    private int failCount;

    @Schema(description = "警告级条数")
    private int warnCount;

    @Schema(description = "是否有阻断级错误（有则不允许提交）")
    private boolean hasBlockingError;

    @Schema(description = "未识别的列头（提示用户核对）")
    private List<String> unknownHeaders;

    @Schema(description = "逐行结果（仅返回失败与警告行）")
    private List<BatchLineResult> results;
}
