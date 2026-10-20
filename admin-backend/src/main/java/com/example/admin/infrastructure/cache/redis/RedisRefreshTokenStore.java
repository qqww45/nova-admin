package com.example.admin.infrastructure.cache.redis;

import com.example.admin.domain.service.RefreshTokenStore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 刷新令牌存储的 Redis 实现。
 *
 * <p>Key 设计：{@code admin:refresh:{userId}:{deviceId}}，TTL 与令牌有效期一致。
 * 同一设备再次刷新会覆盖 Key（天然实现轮换），旧令牌立即失效。
 *
 * <p>{@link #removeByUser} 用 SCAN 遍历：
 * 生产环境禁用 KEYS（阻塞主线程），SCAN 是非阻塞的游标迭代。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "redis")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "admin:refresh:";

    /**
     * 原子轮换脚本：仅当当前存储的 token 与期望值一致时才写入新值。
     *
     * <p>返回 1 表示轮换成功，0 表示当前值不是期望值（已被轮换、已过期或不存在）。
     */
    private static final String ROTATE_LUA =
            "local current = redis.call('GET', KEYS[1])\n"
                    + "if not current then return 0 end\n"
                    + "local ok, entry = pcall(cjson.decode, current)\n"
                    + "if not ok or type(entry) ~= 'table' or entry['token'] ~= ARGV[1] then return 0 end\n"
                    + "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])\n"
                    + "return 1\n";

    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of(ROTATE_LUA, Long.class);

    private final StringRedisTemplate redis;

    private final ObjectMapper objectMapper;

    /** 令牌记录（含过期时间，便于反序列化时直接得到结构） */
    record TokenEntry(String token, String jti, Instant expireAt) {
        @JsonCreator
        TokenEntry(@JsonProperty("token") String token,
                   @JsonProperty("jti") String jti,
                   @JsonProperty("expireAt") Instant expireAt) {
            this.token = token;
            this.jti = jti;
            this.expireAt = expireAt;
        }
    }

    @Override
    public void save(Long userId, String deviceId, String jti, String token, Duration ttl) {
        if (userId == null || deviceId == null || deviceId.isBlank() || ttl == null) {
            return;
        }
        redis.opsForValue().set(key(userId, deviceId),
                RedisJsonSupport.write(objectMapper,
                        new TokenEntry(token, jti, Instant.now().plus(ttl))),
                ttl);
    }

    @Override
    public boolean matches(Long userId, String deviceId, String token) {
        if (userId == null || deviceId == null || token == null || token.isBlank()) {
            return false;
        }
        String json = redis.opsForValue().get(key(userId, deviceId));
        if (json == null) {
            return false;
        }
        TokenEntry entry = RedisJsonSupport.read(objectMapper, json, TokenEntry.class);
        if (entry == null || entry.expireAt().isBefore(Instant.now())) {
            // 过期了让 TTL 自然回收，matches 直接 false
            return false;
        }
        return entry.token().equals(token);
    }

    @Override
    public boolean rotate(Long userId, String deviceId, String oldToken, String newToken, String newJti, Duration ttl) {
        if (userId == null || deviceId == null || deviceId.isBlank()
                || oldToken == null || oldToken.isBlank() || ttl == null) {
            return false;
        }
        String newJson = RedisJsonSupport.write(objectMapper,
                new TokenEntry(newToken, newJti, Instant.now().plus(ttl)));
        // GET → 比较 → SET 必须是一个 Lua 命令：分三条命令的话，
        // 并发刷新会在"比较"与"SET"之间互相穿插，产生与内存实现同样的覆盖问题。
        Long result = redis.execute(ROTATE_SCRIPT, List.of(key(userId, deviceId)),
                oldToken, newJson, String.valueOf(Math.max(1, ttl.getSeconds())));
        return result != null && result == 1L;
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
        String pattern = KEY_PREFIX + userId + ":*";
        List<String> keys = scanKeys(pattern);
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redis.delete(keys);
        int count = deleted == null ? 0 : deleted.intValue();
        if (count > 0) {
            log.info("用户 {} 的 {} 台设备已被强制下线", userId, count);
        }
        return count;
    }

    /**
     * 用 SCAN 扫描匹配的 Key。
     *
     * <p>为什么不用 KEYS：KEYS 会阻塞 Redis 主线程（O(N) 同步遍历整个 Keyspace），
     * 生产大 Keyspace 会卡住所有请求；SCAN 是游标式分批迭代，非阻塞。
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
