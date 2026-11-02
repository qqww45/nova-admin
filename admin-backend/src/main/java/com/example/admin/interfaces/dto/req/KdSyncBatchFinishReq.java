package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批次结束回报。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdSyncBatchFinishReq", description = "批次结束回报")
public class KdSyncBatchFinishReq {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(SUCCESS|FAILED)$", message = "状态只能是 SUCCESS 或 FAILED")
    @Schema(description = "状态：SUCCESS / FAILED")
    private String status;

    @Schema(description = "上行行数")
    private Integer rowCount;

    @Schema(description = "落库行数")
    private Integer upsertCount;

    @Schema(description = "跳过行数")
    private Integer skippedCount;

    @Schema(description = "终点水位（只有成功才推进）")
    private LocalDateTime watermarkTo;

    @Schema(description = "客户端开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "客户端结束时间")
    private LocalDateTime finishedAt;

    @Schema(description = "耗时毫秒")
    private Long costMs;

    @Size(max = 1000, message = "结果信息过长")
    @Schema(description = "结果 / 错误信息")
    private String message;
}
