package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.OnlineSession;

import java.util.List;

/**
 * 在线会话存储（领域接口）。
 *
 * <p>与 {@link RefreshTokenStore} 的分工：
 * 后者管"令牌是否有效"（安全判定），本接口管"当前谁在线、在哪台设备上"（可观测性）。
 * 分开的好处：会话展示需要的字段（IP、浏览器、登录时间）与令牌校验无关，
 * 混在一起会让安全逻辑背负展示需求，也会让敏感令牌被读出来做展示（不必要的暴露面）。
 *
 * <p>Redis 实现建议：
 * <pre>
 * Key   : online:session:{userId}:{deviceId}
 * Value : 会话 JSON
 * TTL   : 与 refresh 令牌一致，过期即视为离线
 * </pre>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface OnlineSessionStore {

    /**
     * 保存（覆盖）会话。
     *
     * @param session 会话
     */
    void save(OnlineSession session);

    /**
     * 查询会话。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return 会话，不存在返回 null
     */
    OnlineSession get(Long userId, String deviceId);

    /**
     * 移除某台设备的会话。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     */
    void remove(Long userId, String deviceId);

    /**
     * 移除用户的全部会话。
     *
     * @param userId 用户 ID
     * @return 被移除的数量
     */
    int removeByUser(Long userId);

    /**
     * 全部在线会话（按最近活跃倒序）。
     *
     * @return 会话列表
     */
    List<OnlineSession> listAll();

    /**
     * 某用户的在线会话。
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    List<OnlineSession> listByUser(Long userId);
}
