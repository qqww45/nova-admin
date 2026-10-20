package com.example.admin.infrastructure.cache.redis;

import com.example.admin.domain.model.auth.ChallengeTicket;
import com.example.admin.domain.service.ChallengeStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 票据存储的 Redis 实现。
 *
 * <p>核心要求：{@link #consume} 必须原子 —— 并发重放时只有一个请求能拿到票据。
 * 用 Lua 脚本把"读 + 删"放在一个 Redis 命令里执行，避免双写竞争。
 *
 * <p>Key 设计：{@code admin:challenge:{ticketId}}，
 * 与配置/会话/设备信任等其他 Key 共享 {@code admin:} 命名空间便于运维区分。
 *
 * <p>启用条件：{@code admin.cache.type=redis} 且应用类路径有 spring-data-redis。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "redis")
public class RedisChallengeStore implements ChallengeStore {

    private static final String KEY_PREFIX = "admin:challenge:";

    /**
     * 原子消费脚本：先 GET，读到再 DEL，返回读取到的值。
     * Redis 6.0+ 也有 GETDEL 命令，但 Lua 脚本兼容性更好（5.x 也支持）。
     */
    private static final RedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if v then redis.call('DEL', KEYS[1]) end "
                    + "return v",
            String.class);

    private final StringRedisTemplate redis;

    private final ObjectMapper objectMapper;

    @Override
    public void save(ChallengeTicket ticket) {
        if (ticket == null) {
            return;
        }
        long ttl = secondsUntil(ticket.getExpireAt());
        if (ttl <= 0) {
            return;
        }
        redis.opsForValue().set(key(ticket.getTicketId()),
                RedisJsonSupport.write(objectMapper, ticket),
                Duration.ofSeconds(ttl));
    }

    @Override
    public ChallengeTicket get(String ticketId) {
        if (ticketId == null) {
            return null;
        }
        String json = redis.opsForValue().get(key(ticketId));
        ChallengeTicket ticket = RedisJsonSupport.read(objectMapper, json, ChallengeTicket.class);
        if (ticket == null) {
            return null;
        }
        if (ticket.isExpired()) {
            // 惰性清理：让过期 Key 自然消失的同时也主动删一下，避免短暂的"读到过期值"窗口
            redis.delete(key(ticketId));
            return null;
        }
        return ticket;
    }

    @Override
    public ChallengeTicket consume(String ticketId, String username, String deviceId, String ipSegment) {
        if (ticketId == null) {
            return null;
        }
        // Lua 脚本保证"读 + 删"原子；并发同时提交同一票据时只有一个能拿到值
        String json = redis.execute(CONSUME_SCRIPT, List.of(key(ticketId)));
        ChallengeTicket ticket = RedisJsonSupport.read(objectMapper, json, ChallengeTicket.class);
        if (ticket == null) {
            log.debug("票据不存在或已被消费（疑似重放）ticketId={}", ticketId);
            return null;
        }
        if (ticket.isExpired()) {
            log.debug("票据已过期 ticketId={}", ticketId);
            return null;
        }
        if (!ticket.matches(username, deviceId, ipSegment)) {
            log.warn("票据上下文不匹配，已作废 ticketId={} 期望用户={}", ticketId, ticket.getUsername());
            return null;
        }
        return ticket;
    }

    @Override
    public void remove(String ticketId) {
        if (ticketId != null) {
            redis.delete(key(ticketId));
        }
    }

    private static String key(String ticketId) {
        return KEY_PREFIX + ticketId;
    }

    /**
     * 计算到指定过期时间的剩余秒数；过期时间已过返回 0。
     */
    private static long secondsUntil(LocalDateTime expireAt) {
        return Math.max(0, Duration.between(LocalDateTime.now(), expireAt).toSeconds());
    }
}
