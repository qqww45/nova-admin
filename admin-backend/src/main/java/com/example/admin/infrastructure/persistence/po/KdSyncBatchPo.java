package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 同步批次持久化对象（对应 kd_sync_batch）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_sync_batch")
@Schema(name = "KdSyncBatchPo", description = "同步批次")
public class KdSyncBatchPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "客户端批次号")
    private String batchNo;

    @Schema(description = "绑定ID")
    private Long bindingId;

    @Schema(description = "数据归属人")
    private Long ownerUserId;

    @Schema(description = "金蝶账套")
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
}
