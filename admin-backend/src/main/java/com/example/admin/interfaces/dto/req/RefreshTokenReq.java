package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "RefreshTokenReq", description = "刷新令牌请求")
public class RefreshTokenReq {

    /** 刷新令牌 */
    @Schema(description = "刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;

    /**
     * 设备标识（可选）。
     *
     * <p>服务端以令牌内携带的 deviceId 为准（不可篡改），
     * 这里仅用于前端冗余上报与问题排查。
     */
    @Schema(description = "设备标识（可选）")
    private String deviceId;
}
