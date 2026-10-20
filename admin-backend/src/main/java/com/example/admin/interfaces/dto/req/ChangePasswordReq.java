package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（个人中心）。
 *
 * <p>安全：必须校验原密码，防止会话劫持后直接改密；
 * 新密码强度由 DTO 校验（长度 5-64），更严格策略可在此扩展（大小写+数字+符号）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ChangePasswordReq", description = "修改密码请求")
public class ChangePasswordReq {

    /** 原密码 */
    @Schema(description = "原密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码 */
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 5, max = 64, message = "新密码长度必须在 5 到 64 之间")
    private String newPassword;

    /** 确认新密码（服务端校验两次输入一致，避免手误） */
    @Schema(description = "确认新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
