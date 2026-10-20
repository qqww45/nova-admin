package com.example.admin.infrastructure.cache.redis;

import com.example.admin.domain.model.auth.OnlineSession;
import com.example.admin.domain.service.OnlineSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 在线会话存储的 Redis 实现。
 *
 * <p>Key 设计：{@code admin:session:{userId}:{deviceId}}，TTL 默认 7 天。
 * 命中列表时遍历用 SCAN（多用户量大时 KEYS 会阻塞主线程）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "redis")
public class RedisOnlineSessionStore implements OnlineSessionStore {

    private static final String KEY_PREFIX = "admin:session:";

    /** 会话默认存活时间：与 refresh 令牌一致 */
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    private final ObjectMapper objectMapper;

    @Override
    public void save(OnlineSession session) {
        if (session == null || session.getUserId() == null) {
            return;
        }
        redis.opsForValue().set(key(session.getUserId(), session.getDeviceId()),
                RedisJsonSupport.write(objectMapper, session),
                DEFAULT_TTL);
    }

    @Override
    public OnlineSession get(Long userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return null;
        }
        String json = redis.opsForValue().get(key(userId, deviceId));
        OnlineSession session = RedisJsonSupport.read(objectMapper, json, OnlineSession.class);
        if (session == null) {
            return null;
        }
        Long ttl = redis.getExpire(key(userId, deviceId), TimeUnit.SECONDS);
        if (ttl != null && ttl <= 0) {
            // 边界：TTL 刚过但 Key 还在
            redis.delete(key(userId, deviceId));
            return null;
        }
        return session;
    }

    @Override
    public void remove(Long userId, String deviceId) {
        if (userId != null && deviceId != null) {
            redis.delete(key(userId, deviceId));
        }
    }

    @Override
    public int removeByUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<String> keys = scanKeys(KEY_PREFIX + userId + ":*");
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redis.delete(keys);
        return deleted == null ? 0 : deleted.intValue();
    }

    @Override
    public List<OnlineSession> listAll() {
        return listSessions(KEY_PREFIX + "*");
    }

    @Override
    public List<OnlineSession> listByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return listSessions(KEY_PREFIX + userId + ":*");
    }

    /**
     * 按模式扫描 Key 并还原为会话对象。
     */
    private List<OnlineSession> listSessions(String pattern) {
        List<String> keys = scanKeys(pattern);
        if (keys.isEmpty()) {
            return List.of();
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null) {
            return List.of();
        }
        // 逐个 getExpire 是 N 次网络往返：500 个在线会话就是 500 次 RTT，
        // 在线列表每刷新一次都要付这个代价。用 pipeline 一次取完全部 TTL。
        List<Object> ttlResults = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.keyCommands().ttl(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        List<String> expiredKeys = new ArrayList<>();
        List<OnlineSession> sessions = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < keys.size() && i < values.size(); i++) {
            String json = values.get(i);
            if (json == null) {
                continue;
            }
            OnlineSession session = RedisJsonSupport.read(objectMapper, json, OnlineSession.class);
            if (session == null) {
                continue;
            }
            Long ttl = i < ttlResults.size() && ttlResults.get(i) instanceof Long value ? value : null;
            if (ttl != null && ttl <= 0) {
                expiredKeys.add(keys.get(i));
                continue;
            }
            sessions.add(session);
        }
        if (!expiredKeys.isEmpty()) {
            redis.delete(expiredKeys);
        }
        return sessions.stream()
                .sorted(Comparator.comparing(OnlineSession::getLastAccessTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 用 SCAN 扫描匹配的 Key。
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    private static String key(Long userId, String deviceId) {
        return KEY_PREFIX + userId + ":" + deviceId;
    }
}
