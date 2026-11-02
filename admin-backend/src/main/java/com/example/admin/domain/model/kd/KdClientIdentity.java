package com.example.admin.domain.model.kd;

/**
 * 已认证的采集器身份（只读）。
 *
 * @param bindingId 绑定 ID
 * @param userId    nova 用户 ID（数据归属人）
 * @param acctId    金蝶账套 ID
 * @author admin-system
 * @since 2026-11-02
 */
public record KdClientIdentity(Long bindingId, Long userId, String acctId) {
}
