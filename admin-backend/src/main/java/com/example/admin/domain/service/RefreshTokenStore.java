package com.example.admin.domain.service;

import java.time.Duration;

/**
 * 刷新令牌存储（领域接口）。
 *
 * <p>为什么需要它：JWT 无状态，refresh 令牌一旦签发，服务端无法天然作废；
 * 只有把"当前有效的 refresh 令牌"存在服务端，才能实现三件事：
 * <ol>
 *     <li><b>轮换</b>：每次刷新都换发新令牌并作废旧的，旧令牌被盗用也只能生效一次；</li>
 *     <li><b>按设备踢人</b>：删除某个 {@code userId:deviceId} 的记录即可让该设备下线；</li>
 *     <li><b>全设备下线</b>：删除用户下的全部记录（改密、封号场景）。</li>
 * </ol>
 *
 * <p>Redis Key 设计（生产实现）：
 * <pre>
 * Key   : refresh_token:{userId}:{deviceId}
 * Value : 当前有效的 refresh 令牌（含 jti）
 * TTL   : 与 refresh 令牌有效期一致（7~30 天），自动过期无需清理
 * </pre>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface RefreshTokenStore {

    /**
     * 保存（覆盖）某设备当前有效的刷新令牌。
     *
     * @param userId    用户 ID
     * @param deviceId  设备标识
     * @param jti       令牌唯一标识
     * @param token     令牌字符串
     * @param ttl       有效期
     */
    void save(Long userId, String deviceId, String jti, String token, Duration ttl);

    /**
     * 校验令牌是否为该设备当前有效的刷新令牌。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @param token    令牌字符串
     * @return true 表示有效
     */
    boolean matches(Long userId, String deviceId, String token);

    /**
     * 原子轮换：仅当"存储中当前值 == oldToken"时，才写入 newToken。
     *
     * <p>为什么需要原子性：若调用方分两步做（先 {@link #matches} 再 {@link #save}），
     * 并发的两个刷新请求会同时通过校验，然后各自写入自己的新令牌 —— 后写覆盖先写，
     * 先返回的那个令牌立刻变成"非当前值"，用户表现为被随机踢下线。
     * 轮换做得越勤，撞上这个窗口的概率越高。
     *
     * <p>实现要求：内存实现用 {@code ConcurrentHashMap.compute}，
     * Redis 实现用 Lua 脚本把"比较 + 写入"合并成一个命令。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @param oldToken 期望的当前有效令牌
     * @param newToken 新令牌
     * @param newJti   新令牌的 jti
     * @param ttl      有效期
     * @return true 表示轮换成功；false 表示 oldToken 已不是当前有效令牌（应拒绝本次刷新）
     */
    boolean rotate(Long userId, String deviceId, String oldToken, String newToken, String newJti, Duration ttl);

    /**
     * 删除某设备的刷新令牌（该设备下次刷新即失败 → 需重新登录）。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     */
    void remove(Long userId, String deviceId);

    /**
     * 删除用户的全部刷新令牌（服务端主动踢人 / 全设备下线）。
     *
     * @param userId 用户 ID
     * @return 被下线的设备数量
     */
    int removeByUser(Long userId);
}
