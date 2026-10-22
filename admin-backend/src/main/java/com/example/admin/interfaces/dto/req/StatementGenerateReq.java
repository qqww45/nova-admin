package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对账单生成请求（聚合后落库为签发快照）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "StatementGenerateReq", description = "对账单生成请求")
public class StatementGenerateReq {

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

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
