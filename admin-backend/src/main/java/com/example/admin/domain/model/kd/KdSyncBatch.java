package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步批次（一次同步 = 一个批次，可含多个实体）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdSyncBatch {

    /** 主键 */
    private Long id;

    /** 客户端批次号 */
    private String batchNo;

    /** 绑定 ID */
    private Long bindingId;

    /** 数据归属人 */
    private Long ownerUserId;

    /** 账套 */
    private String acctId;

    /** 实体：customer / outbound / outbound_line / ALL */
    private String entity;

    /** 模式：INCREMENTAL / FULL */
    private String mode;

    /** 状态：RUNNING / SUCCESS / FAILED */
    private String status;

    /** 上行行数 */
    private Integer rowCount;

    /** 落库行数 */
    private Integer upsertCount;

    /** 跳过行数 */
    private Integer skippedCount;

    /** 起点水位 */
    private LocalDateTime watermarkFrom;

    /** 终点水位 */
    private LocalDateTime watermarkTo;

    /** 客户端开始时间 */
    private LocalDateTime startedAt;

    /** 客户端结束时间 */
    private LocalDateTime finishedAt;

    /** 耗时毫秒 */
    private Long costMs;

    /** 客户端版本 */
    private String clientVersion;

    /** 结果 / 错误信息 */
    private String message;

    /** 创建时间 */
    private LocalDateTime createTime;
}
