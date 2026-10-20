package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 请求下发一次性验证码（强验证用）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "OneTimeCodeReq", description = "请求下发一次性验证码")
public class OneTimeCodeReq {

    /** 验证票据 ID */
    @Schema(description = "验证票据ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "验证票据不能为空")
    private String ticketId;

    /** 用户名 */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 设备标识 */
    @Schema(description = "设备标识")
    private String deviceId;
}
