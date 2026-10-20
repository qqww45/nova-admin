package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置用户密码请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ResetPasswordReq", description = "重置密码请求")
public class ResetPasswordReq {

    /** 用户 ID */
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /** 新密码 */
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 5, max = 64, message = "密码长度必须在 5 到 64 之间")
    private String password;
}
