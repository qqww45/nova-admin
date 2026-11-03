package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步批次响应。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdSyncBatchResp", description = "同步批次")
public class KdSyncBatchResp {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "批次号")
    private String batchNo;

    @Schema(description = "绑定 ID")
    private String bindingId;

    @Schema(description = "数据归属人")
    private String ownerUserId;

    @Schema(description = "数据归属人姓名（管理员排查时辨认业务员）")
    private String ownerUserName;

    @Schema(description = "账套")
    private String acctId;

    @Schema(description = "实体")
    private String entity;

    @Schema(description = "模式")
    private String mode;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "上行行数")
    private Integer rowCount;

    @Schema(description = "落库行数")
    private Integer upsertCount;

    @Schema(description = "跳过行数")
    private Integer skippedCount;

    @Schema(description = "起点水位")
    private LocalDateTime watermarkFrom;

    @Schema(description = "终点水位")
    private LocalDateTime watermarkTo;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;

    @Schema(description = "耗时毫秒")
    private Long costMs;

    @Schema(description = "客户端版本")
    private String clientVersion;

    @Schema(description = "结果信息")
    private String message;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
