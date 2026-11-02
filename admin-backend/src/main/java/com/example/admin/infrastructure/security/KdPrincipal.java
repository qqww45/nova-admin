package com.example.admin.infrastructure.security;

/**
 * 已认证的采集器身份。
 *
 * <p>与 JWT 的 {@code LoginUser} 区分开：采集器令牌只能访问采集接口，
 * 不能拿来调后台管理接口，反之亦然。
 *
 * @param bindingId 绑定 ID
 * @param userId    nova 用户 ID（数据归属人）
 * @param acctId    金蝶账套 ID
 * @author admin-system
 * @since 2026-11-02
 */
public record KdPrincipal(Long bindingId, Long userId, String acctId) {
}
