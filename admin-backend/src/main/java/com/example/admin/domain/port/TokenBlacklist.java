package com.example.admin.domain.port;

import java.time.LocalDateTime;

/**
 * 令牌黑名单端口：让"登出 / 踢下线 / 令牌轮换"真正生效。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface TokenBlacklist {

    /**
     * 拉黑令牌。
     *
     * @param jti       令牌唯一标识
     * @param tokenType 令牌类型（access / refresh）
     * @param userId    所属用户
     * @param expireAt  失效时刻（超过该时刻的记录可被清理）
     */
    void add(String jti, String tokenType, Long userId, LocalDateTime expireAt);

    /**
     * 判断令牌是否已被拉黑。
     *
     * @param jti 令牌唯一标识
     * @return true 表示已失效
     */
    boolean contains(String jti);
}
