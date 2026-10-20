package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改启停状态请求（用户/角色通用）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "UpdateStatusReq", description = "修改状态请求")
public class UpdateStatusReq {

    /** 目标 ID */
    @Schema(description = "目标ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "ID不能为空")
    private String id;

    /** 目标状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;
}
