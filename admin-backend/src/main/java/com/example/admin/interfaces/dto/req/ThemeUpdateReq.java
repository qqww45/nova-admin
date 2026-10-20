package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新主题偏好请求。
 *
 * <p>为什么后端要存主题：用户在 A 设备切换到暗色模式后，在 B 设备登录应保持一致，
 * 因此主题偏好属于“用户配置”，需要持久化到用户表（前端同时缓存一份保证首屏无闪烁）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ThemeUpdateReq", description = "更新主题偏好请求")
public class ThemeUpdateReq {

    /** 主题：light / dark / auto */
    @Schema(description = "主题：light/dark/auto", requiredMode = Schema.RequiredMode.REQUIRED, example = "dark")
    @NotBlank(message = "主题不能为空")
    @Pattern(regexp = "^(light|dark|auto)$", message = "主题只能是 light、dark 或 auto")
    private String theme;
}
