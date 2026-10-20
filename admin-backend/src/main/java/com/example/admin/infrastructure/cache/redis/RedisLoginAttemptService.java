package com.example.admin.infrastructure.cache.redis;

import com.example.admin.common.constant.Constants;
import com.example.admin.domain.service.LoginPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录风控的 Redis 实现。
 *
 * <p>三种计数 Key（与内存实现语义一致）：
 * <pre>
 *   admin:login:fail:{userName}     失败计数（窗口期内有效）
 *   admin:login:lock:{userName}     锁定标记（达到阈值后置入）
 *   admin:login:ip:{ip}             单 IP 请求计数（1 分钟窗口）
 * </pre>
 *
 * <p>多实例下也只共用一个 Redis：阈值不会被放大 N 倍（每个实例各计数），
 * 锁定状态与失败计数也是全局共享，攻击者无法通过换实例"重置"计数。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "redis")
public class RedisLoginAttemptService implements LoginPolicy {

    /** 允许的最大连续失败次数 */
    @Value("${admin.login.max-retry:5}")
    private int maxRetry;

    /** 失败计数统计窗口（秒） */
    @Value("${admin.login.retry-window-seconds:300}")
    private long retryWindowSeconds;

    /** 锁定时长（秒） */
    @Value("${admin.login.lock-seconds:600}")
    private long lockSeconds;

    /** 单 IP 每分钟登录请求上限 */
    @Value("${admin.login.ip-limit-per-minute:20}")
    private int ipLimitPerMinute;

    private final StringRedisTemplate redis;

    @Override
    public void checkIpLimit(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        String key = Constants.IP_LIMIT_KEY_PREFIX + ip;
        Long count = redis.opsForValue().increment(key);
        // INCR 对新 Key 会自动置 1，需要单独设 EXPIRE；这里统一在每次都设（同一 key 重复 EXPIRE 会被覆盖）
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(1));
        }
        if (count != null && count > ipLimitPerMinute) {
            log.warn("IP {} 登录请求过于频繁，已触发限流 count={}", ip, count);
            throw new IllegalStateException("IP_LIMITED");
        }
    }

    @Override
    public void checkAccountLocked(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        Boolean exists = redis.hasKey(Constants.LOGIN_LOCK_KEY_PREFIX + username);
        if (Boolean.TRUE.equals(exists)) {
            log.warn("账号 {} 处于锁定状态，拒绝登录", username);
            throw new IllegalStateException("ACCOUNT_LOCKED");
        }
    }

    @Override
    public void onLoginFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        String failKey = Constants.LOGIN_FAIL_KEY_PREFIX + username;
        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redis.expire(failKey, Duration.ofSeconds(retryWindowSeconds));
        }
        if (count != null && count >= maxRetry) {
            // 达到阈值：置入锁定 + 清空失败计数（避免解封后立刻又锁）
            redis.opsForValue().set(Constants.LOGIN_LOCK_KEY_PREFIX + username, "locked",
                    Duration.ofSeconds(lockSeconds));
            redis.delete(failKey);
            log.warn("账号 {} 连续失败 {} 次，已锁定 {} 秒", username, count, lockSeconds);
        }
    }

    @Override
    public void onLoginSuccess(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        redis.delete(Constants.LOGIN_FAIL_KEY_PREFIX + username);
        redis.delete(Constants.LOGIN_LOCK_KEY_PREFIX + username);
    }

    @Override
    public int failureCount(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        String value = redis.opsForValue().get(Constants.LOGIN_FAIL_KEY_PREFIX + username);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            // 异常数据：当作 0 处理
            return 0;
        }
    }
}
