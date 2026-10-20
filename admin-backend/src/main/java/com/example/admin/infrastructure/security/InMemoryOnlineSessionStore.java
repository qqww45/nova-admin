package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.auth.OnlineSession;
import com.example.admin.domain.service.OnlineSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线会话存储的内存实现。
 *
 * <p>与刷新令牌的关系：会话的"存活"应与 refresh 令牌同生命周期。
 * 这里用固定上限时间（默认与 refresh 令牌一致：7 天）做惰性过期，
 * 生产换 Redis 后直接依赖 TTL 即可，无需任何清理逻辑。
 *
 * <p>内存实现的边界：应用重启后会话列表为空（重启即所有人离线，符合实际），
 * 多实例部署需换 Redis 实现，否则各实例只能看到自己的那部分会话。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryOnlineSessionStore implements OnlineSessionStore {

    /** 会话默认存活时间（秒）：与 refresh 令牌有效期一致 */
    private static final long DEFAULT_TTL_SECONDS = 7 * 24 * 3600L;

    /** key = "{userId}:{deviceId}" */
    private final Map<String, OnlineSession> store = new ConcurrentHashMap<>();

    /** 最近活跃时间（单独维护，避免与会话对象的可变状态耦合） */
    private final Map<String, Instant> expireAt = new ConcurrentHashMap<>();

    @Override
    public void save(OnlineSession session) {
        if (session == null || session.getUserId() == null) {
            return;
        }
        String key = key(session.getUserId(), session.getDeviceId());
        store.put(key, session);
        expireAt.put(key, Instant.now().plusSeconds(DEFAULT_TTL_SECONDS));
    }

    @Override
    public OnlineSession get(Long userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return null;
        }
        OnlineSession session = store.get(key(userId, deviceId));
        if (session != null && isExpired(key(userId, deviceId))) {
            remove(userId, deviceId);
            return null;
        }
        return session;
    }

    @Override
    public void remove(Long userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return;
        }
        String key = key(userId, deviceId);
        store.remove(key);
        expireAt.remove(key);
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
        keys.forEach(key -> {
            store.remove(key);
            expireAt.remove(key);
        });
        return keys.size();
    }

    @Override
    public List<OnlineSession> listAll() {
        return list().stream()
                .sorted(Comparator.comparing(OnlineSession::getLastAccessTime).reversed())
                .toList();
    }

    @Override
    public List<OnlineSession> listByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return listAll().stream()
                .filter(session -> userId.equals(session.getUserId()))
                .toList();
    }

    /**
     * 列出未过期的会话（惰性清理）。
     *
     * @return 会话列表
     */
    private List<OnlineSession> list() {
        List<OnlineSession> sessions = new ArrayList<>();
        for (Map.Entry<String, OnlineSession> entry : store.entrySet()) {
            if (isExpired(entry.getKey())) {
                store.remove(entry.getKey());
                expireAt.remove(entry.getKey());
                continue;
            }
            sessions.add(entry.getValue());
        }
        return sessions;
    }

    /**
     * 清理过期会话（每小时一次）。
     */
    @Scheduled(fixedDelay = 3600_000)
    public void cleanExpired() {
        int before = store.size();
        list();
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("清理过期在线会话 {} 条", removed);
        }
    }

    /**
     * 判断会话是否已过期。
     *
     * @param key 存储键
     * @return true 表示已过期
     */
    private boolean isExpired(String key) {
        Instant deadline = expireAt.get(key);
        return deadline == null || deadline.isBefore(Instant.now());
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

    /**
     * 会话存活时间（秒），便于测试与配置化。
     *
     * @return 存活秒数
     */
    public long ttlSeconds() {
        return DEFAULT_TTL_SECONDS;
    }

    /**
     * 当前在线会话数量（用于页面统计"当前在线"）。
     *
     * @return 会话数量
     */
    public int count() {
        return list().size();
    }

    /**
     * 将会话过期时间换算为展示用时间。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return 过期时间，不存在返回 null
     */
    public LocalDateTime expireTimeOf(Long userId, String deviceId) {
        Instant deadline = expireAt.get(key(userId, deviceId));
        return deadline == null ? null : LocalDateTime.ofInstant(deadline, java.time.ZoneId.systemDefault());
    }
}
