package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应：令牌与用户简要信息。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LoginResp", description = "登录响应")
public class LoginResp {

    /** 访问令牌 */
    @Schema(description = "访问令牌")
    private String accessToken;

    /** 刷新令牌 */
    @Schema(description = "刷新令牌")
    private String refreshToken;

    /** 令牌类型，固定 Bearer */
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    /** 访问令牌有效期（秒） */
    @Schema(description = "访问令牌有效期（秒）", example = "7200")
    private Long expiresIn;

    /** 用户 ID */
    @Schema(description = "用户ID")
    private String userId;

    /** 用户名 */
    @Schema(description = "用户名")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /**
     * 设备标识（回传给前端，用于刷新令牌时按设备维度校验）。
     *
     * <p>前端应把它持久化（localStorage），保证同一设备每次登录得到稳定的 ID。
     */
    @Schema(description = "设备标识")
    private String deviceId;

    /** 该设备是否已纳入可信列表（true 表示后续登录免验证码） */
    @Schema(description = "设备是否已可信", example = "true")
    private Boolean trusted;
}
