package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * <p>校验说明：所有入参在 DTO 层用注解声明式校验（Controller 只加 {@code @Validated}），
 * 禁止在 Controller/Service 中手写 if 判断。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "LoginReq", description = "登录请求")
public class LoginReq {

    /** 用户名 */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64, message = "用户名长度必须在 2 到 64 之间")
    private String username;

    /** 密码（前端传输明文，务必走 HTTPS） */
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin123")
    @NotBlank(message = "密码不能为空")
    @Size(min = 5, max = 64, message = "密码长度必须在 5 到 64 之间")
    private String password;

    // 图形验证码字段已移除：登录主流程是风险分层验证（无风险直接放行、有风险则下发一次性票据，
    // 由票据驱动滑块或一次性码校验），这两个字段服务端从未读取 ——
    // 留着只会让人误以为存在"图形验证码"这道防线（安全控制错觉比没有更危险）。

    /**
     * 设备标识（前端生成并持久化，同一设备保持稳定）。
     *
     * <p>未传时后端会根据 UA + IP 段 + 指纹自行推导，但推荐前端传入：
     * 指纹在前端计算更准确（可用 canvas / 字体 / 屏幕等特征）。
     */
    @Schema(description = "设备标识", example = "6f1d2c9a8b7e6f5a4d3c2b1a0f9e8d7c")
    private String deviceId;

    /** 浏览器指纹哈希（前端计算，用于合成设备标识） */
    @Schema(description = "浏览器指纹哈希")
    private String fingerprint;

    /**
     * 是否信任此设备（默认 true）。
     *
     * <p>勾选后本次二次校验通过即写入信任记录（30 天），此后该设备免验证码。
     */
    @Schema(description = "是否信任此设备", example = "true")
    private Boolean trustDevice = true;

    /** 是否记住我（延长令牌有效期，当前实现为前端保留登录态） */
    @Schema(description = "是否记住我", example = "false")
    private Boolean rememberMe = false;
}
