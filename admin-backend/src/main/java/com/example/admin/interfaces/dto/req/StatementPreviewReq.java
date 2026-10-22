package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对账单预览请求（按客户 + 期间实时聚合，不落库）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "StatementPreviewReq", description = "对账单预览请求")
public class StatementPreviewReq {

    /** 客户 ID */
    @NotNull(message = "客户不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    /** 对账期间起 */
    @Schema(description = "对账期间起")
    private LocalDateTime startDate;

    /** 对账期间止 */
    @Schema(description = "对账期间止")
    private LocalDateTime endDate;
}
