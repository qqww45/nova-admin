package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务操作日志持久化对象（对应 biz_operation_log）。
 *
 * <p>日志只增不改不删，因此不继承 {@code BaseEntity}（无逻辑删除字段）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@TableName("biz_operation_log")
@Schema(name = "BizOperationLog", description = "业务操作日志")
public class BizOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @Schema(description = "主键ID")
    private Long id;

    /** 模块 */
    @Schema(description = "模块")
    private String module;

    /** 业务 ID */
    @Schema(description = "业务ID")
    private String bizId;

    /** 动作 */
    @Schema(description = "动作")
    private String action;

    /** 变更前 JSON */
    @Schema(description = "变更前JSON")
    private String beforeJson;

    /** 变更后 JSON */
    @Schema(description = "变更后JSON")
    private String afterJson;

    /** 操作原因 */
    @Schema(description = "操作原因")
    private String reason;

    /** 操作人 ID */
    @Schema(description = "操作人ID")
    private Long operatorId;

    /** 操作人姓名（历史快照） */
    @Schema(description = "操作人姓名")
    private String operatorName;

    /** 操作时间 */
    @Schema(description = "操作时间")
    private LocalDateTime operateTime;
}
