package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志视图对象。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "OperationLogResp", description = "操作日志")
public class OperationLogResp {

    @Schema(description = "日志ID")
    private String id;

    @Schema(description = "模块：CUSTOMER / ORDER / SHIPMENT / IMPORT")
    private String module;

    @Schema(description = "模块名称")
    private String moduleLabel;

    @Schema(description = "业务ID")
    private String bizId;

    @Schema(description = "动作：CREATE / UPDATE / VOID / IMPORT / ROLLBACK / RECONCILE")
    private String action;

    @Schema(description = "动作名称")
    private String actionLabel;

    @Schema(description = "变更前快照")
    private String beforeJson;

    @Schema(description = "变更后快照")
    private String afterJson;

    @Schema(description = "操作原因（敏感操作必填）")
    private String reason;

    @Schema(description = "操作人ID")
    private String operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "操作时间")
    private LocalDateTime operateTime;
}
