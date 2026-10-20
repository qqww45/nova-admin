package com.example.admin.domain.port;

/**
 * 令牌内容（领域视角）：只保留应用层真正需要的字段。
 *
 * @param jti      令牌唯一标识（用于拉黑）
 * @param subject  用户名
 * @param type     令牌类型（access / refresh）
 * @param deviceId 设备标识（刷新令牌才有），可能为 null
 * @author admin-system
 * @since 2026-01-01
 */
public record TokenClaims(
        String jti,
        String subject,
        String type,
        String deviceId
) {
}
