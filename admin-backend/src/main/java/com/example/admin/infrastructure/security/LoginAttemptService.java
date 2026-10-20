package com.example.admin.infrastructure.security;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import com.example.admin.common.constant.Constants;
import com.example.admin.domain.service.LoginPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 登录风控服务：失败次数限制 + 账号锁定 + IP 级限流。
 *
 * <p>分层说明：本类是领域策略接口 {@link LoginPolicy} 的基础设施实现。
 * 领域层只声明"要限流、要锁定"的规则，具体用什么存储（内存 / Redis）由这里决定，
 * 后续替换为 Redis 时领域层与应用层零改动。
 *
 * <p>策略（可通过配置调整）：
 * <ul>
 *     <li>同一账号 5 分钟内连续失败 5 次 → 锁定 10 分钟；</li>
 *     <li>同一 IP 每分钟最多请求登录接口 20 次（防暴力破解与脚本刷接口）。</li>
 * </ul>
 *
 * <p>实现说明：使用 Hutool 带过期时间的内存缓存，单机场景足够；
 * 生产多实例部署建议替换为 Redis（INCR + EXPIRE），本类方法签名保持不变即可平滑替换。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "memory", matchIfMissing = true)
public class LoginAttemptService implements LoginPolicy {

    /** IP 限流窗口（毫秒） */
    private static final long IP_LIMIT_WINDOW_MILLIS = 60 * 1000L;

    /**
     * 取 key 对应的锁段（同一 key 恒定落在同一段）。
     *
     * @param key   缓存 key
     * @param locks 锁数组
     * @return 该 key 应使用的锁
     */
    private static Object lockFor(String key, Object[] locks) {
        return locks[Math.floorMod(key == null ? 0 : key.hashCode(), locks.length)];
    }

    private static Object[] newLocks() {
        Object[] locks = new Object[LOCK_SEGMENTS];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    /**
     * 缓存超过上限时先清理过期项，仍超则整体清空。
     *
     * @param cache 限流缓存
     */
    private static void evictIfOversized(Cache<?, ?> cache) {
        if (cache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        cache.prune();
        if (cache.size() > MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }

    /**
     * 限流缓存条目上限。
     *
     * <p>为什么必须有上限：IP 维度是可以无限枚举的（伪造 X-Forwarded-For 更是随手就来），
     * 只靠 TTL 兜底，攻击者在窗口期内灌满不同 IP 就能把内存吃光。
     * 超过上限时先清理已过期的，仍超则整体清空 —— 限流计数是可重建的数据，
     * 丢掉最多让计数重新开始，总好过内存耗尽。
     */
    private static final int MAX_CACHE_ENTRIES = 10_000;

    /**
     * 分段锁：按 key 的哈希取段，不同 key 之间不互相阻塞。
     *
     * <p>为什么不用一把全局锁：所有 IP、所有账号共用一把锁时，
     * 登录高峰会退化成串行排队，而且一个热 key 的争用会拖慢所有人。
     * 分段之后冲突概率降到 1/段数，"读-改-写"的原子性照样有保证
     * （同一个 key 永远落在同一段上）。
     */
    private static final int LOCK_SEGMENTS = 64;

    /** IP 计数分段锁 */
    private final Object[] ipLocks = newLocks();

    /** 失败计数分段锁 */
    private final Object[] failLocks = newLocks();

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

    /** 账号失败次数缓存（窗口期内有效） */
    private final Cache<String, Integer> failCache = CacheUtil.newTimedCache(300 * 1000L);

    /** 账号锁定缓存 */
    private final Cache<String, String> lockCache = CacheUtil.newTimedCache(600 * 1000L);

    /** IP 请求计数缓存（1 分钟窗口） */
    private final Cache<String, Integer> ipCache = CacheUtil.newTimedCache(60 * 1000L);

    /**
     * 校验账号是否被锁定（登录前调用）。
     *
     * @param username 用户名
     * @throws BusinessException 账号被锁定时抛出
     */
    @Override
    public void checkAccountLocked(String username) {
        String lockKey = Constants.LOGIN_LOCK_KEY_PREFIX + username;
        if (lockCache.get(lockKey) != null) {
            log.warn("账号 {} 处于锁定状态，拒绝登录", username);
            // 抛领域层可识别的异常，由应用服务转换为业务异常（基础设施不直接依赖 Web 层异常体系）
            throw new IllegalStateException("ACCOUNT_LOCKED");
        }
    }

    /**
     * 校验 IP 是否触发限流（登录前调用）。
     *
     * @param ip 客户端 IP
     * @throws BusinessException 请求过于频繁时抛出
     */
    @Override
    public void checkIpLimit(String ip) {
        String ipKey = Constants.IP_LIMIT_KEY_PREFIX + ip;
        // "读 → 判断 → 写"三步必须是一个整体：并发下多个线程会读到同一个旧值，
        // 各自 +1 后互相覆盖，20 次/分钟 的阈值被放大成"几乎不限流"。
        // 临界区只有几次 Map 操作，共用一把锁不影响吞吐。
        evictIfOversized(ipCache);
        synchronized (lockFor(ipKey, ipLocks)) {
            Integer count = ipCache.get(ipKey);
            if (count == null) {
                ipCache.put(ipKey, 1, IP_LIMIT_WINDOW_MILLIS);
                return;
            }
            if (count >= ipLimitPerMinute) {
                log.warn("IP {} 登录请求过于频繁，已触发限流", ip);
                throw new IllegalStateException("IP_LIMITED");
            }
            ipCache.put(ipKey, count + 1, IP_LIMIT_WINDOW_MILLIS);
        }
    }

    /**
     * 记录登录失败：累计次数，达到阈值则锁定账号。
     *
     * @param username 用户名
     */
    @Override
    public void onLoginFailure(String username) {
        String failKey = Constants.LOGIN_FAIL_KEY_PREFIX + username;
        // 同 checkIpLimit：并发失败时若各自读到旧值，计数会少算，
        // 5 次锁定可能变成"怎么都锁不上"，账号锁定这道防线就失效了。
        evictIfOversized(failCache);
        synchronized (lockFor(failKey, failLocks)) {
            Integer count = failCache.get(failKey);
            int newCount = (count == null ? 0 : count) + 1;
            failCache.put(failKey, newCount, retryWindowSeconds * 1000L);

            if (newCount >= maxRetry) {
                lockCache.put(Constants.LOGIN_LOCK_KEY_PREFIX + username, "locked", lockSeconds * 1000L);
                failCache.remove(failKey);
                log.warn("账号 {} 连续失败 {} 次，已锁定 {} 秒", username, newCount, lockSeconds);
            }
        }
    }

    /**
     * 登录成功：清除失败计数与锁定状态。
     *
     * @param username 用户名
     */
    @Override
    public void onLoginSuccess(String username) {
        failCache.remove(Constants.LOGIN_FAIL_KEY_PREFIX + username);
        lockCache.remove(Constants.LOGIN_LOCK_KEY_PREFIX + username);
    }

    /**
     * 查询账号近期连续失败次数（风险评级输入）。
     *
     * @param username 用户名
     * @return 窗口期内的失败次数
     */
    @Override
    public int failureCount(String username) {
        Integer count = failCache.get(Constants.LOGIN_FAIL_KEY_PREFIX + username);
        return count == null ? 0 : count;
    }

    /**
     * 查询账号剩余可尝试次数（用于前端友好提示）。
     *
     * @param username 用户名
     * @return 剩余次数
     */
    public int remainingRetries(String username) {
        Integer count = failCache.get(Constants.LOGIN_FAIL_KEY_PREFIX + username);
        if (count == null) {
            return maxRetry;
        }
        return Math.max(0, maxRetry - count);
    }
}
