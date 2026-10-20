package com.example.admin.infrastructure.security;

import com.example.admin.domain.service.RefreshTokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 刷新令牌存储的内存实现。
 *
 * <p>语义与 Redis 实现完全一致（便于无缝替换）：
 * <pre>
 *     SET refresh_token:{userId}:{deviceId} {token} EX {ttl}
 * </pre>
 * 一个用户可以在多台设备登录（每条记录一个设备），
 * 每条记录只保留"当前有效"的那个 refresh 令牌 —— 刷新即覆盖，天然实现轮换。
 *
 * <p>为什么用"覆盖"而不是"追加"：
 * 若允许同一设备存在多个有效 refresh 令牌，攻击者拿到旧令牌也能一直续期，
 * 轮换就失去了意义。覆盖写保证同一设备同一时刻只有一个有效令牌。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    /** 令牌记录（含过期时间，模拟 Redis TTL） */
    private record TokenEntry(String token, String jti, Instant expireAt) {
    }

    /** key = "{userId}:{deviceId}" */
    private final Map<String, TokenEntry> store = new ConcurrentHashMap<>();

    @Override
    public void save(Long userId, String deviceId, String jti, String token, Duration ttl) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        store.put(key(userId, deviceId), new TokenEntry(token, jti, Instant.now().plus(ttl)));
    }

    @Override
    public boolean matches(Long userId, String deviceId, String token) {
        if (userId == null || deviceId == null || token == null || token.isBlank()) {
            return false;
        }
        TokenEntry entry = store.get(key(userId, deviceId));
        if (entry == null) {
            return false;
        }
        if (entry.expireAt().isBefore(Instant.now())) {
            store.remove(key(userId, deviceId));
            return false;
        }
        // 与"存储中当前有效令牌"比对：不一致说明是旧的（已轮换）或伪造的
        return entry.token().equals(token);
    }

    @Override
    public boolean rotate(Long userId, String deviceId, String oldToken, String newToken, String newJti, Duration ttl) {
        if (userId == null || deviceId == null || deviceId.isBlank()
                || oldToken == null || oldToken.isBlank()) {
            return false;
        }
        // compute 对同一 key 是原子的：比较与写入之间不会插入其他线程的写
        boolean[] rotated = {false};
        store.compute(key(userId, deviceId), (k, entry) -> {
            if (entry == null || entry.expireAt().isBefore(Instant.now()) || !entry.token().equals(oldToken)) {
                // 不是当前有效令牌（已被轮换 / 已过期 / 不匹配）：保持原样，拒绝本次刷新
                return entry;
            }
            rotated[0] = true;
            return new TokenEntry(newToken, newJti, Instant.now().plus(ttl));
        });
        return rotated[0];
    }

    @Override
    public void remove(Long userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return;
        }
        store.remove(key(userId, deviceId));
    }

    @Override
    public int removeByUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        String prefix = userId + ":";
        List<String> keys = new ArrayList<>();
        for (String key : store.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        keys.forEach(store::remove);
        if (!keys.isEmpty()) {
            log.info("用户 {} 的 {} 台设备已被强制下线", userId, keys.size());
        }
        return keys.size();
    }

    /**
     * 清理过期记录（每小时执行一次，等价于 Redis 的自动过期）。
     */
    @Scheduled(fixedDelay = 3600_000)
    public void cleanExpired() {
        Instant now = Instant.now();
        int removed = 0;
        for (Map.Entry<String, TokenEntry> entry : store.entrySet()) {
            if (entry.getValue().expireAt().isBefore(now)) {
                store.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("清理过期刷新令牌 {} 条", removed);
        }
    }

    /**
     * 构造存储键。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return 存储键
     */
    private String key(Long userId, String deviceId) {
        return userId + ":" + deviceId;
    }
}
