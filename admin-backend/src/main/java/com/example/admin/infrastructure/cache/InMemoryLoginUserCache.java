package com.example.admin.infrastructure.cache;

import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.port.LoginUserCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录用户缓存的进程内实现。
 *
 * <p>三条约束缺一不可：
 * <ol>
 *     <li><b>只存副本</b>：调用方拿到的永远是 {@link LoginUser#copyForRequest()} 的结果，
 *         否则多个请求会共享同一个对象并互相覆盖 tokenId / deviceId；</li>
 *     <li><b>有 TTL</b>：即使某个失效点漏了，权限也不会永远停在旧状态；</li>
 *     <li><b>有容量上限</b>：缓存用户名可能很多（批量导入、机器人注册），
 *         无上限就是一条内存泄漏路径。</li>
 * </ol>
 *
 * <p>多实例部署时请换 Redis 实现：进程内缓存只在本实例生效，
 * 权限变更在 A 实例发生，B 实例的缓存要等 TTL 才失效。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
public class InMemoryLoginUserCache implements LoginUserCache {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 缓存有效期（秒）：兜底，正常情况靠显式失效 */
    @Value("${admin.security.login-user-cache-ttl-seconds:300}")
    private long ttlSeconds;

    /** 容量上限：达到后整体清空，绝不让缓存无界增长 */
    @Value("${admin.security.login-user-cache-max-size:2000}")
    private int maxSize;

    @Override
    public LoginUser get(String username) {
        if (username == null) {
            return null;
        }
        CacheEntry entry = cache.get(username);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(username, entry);
            return null;
        }
        return entry.value().copyForRequest();
    }

    @Override
    public void put(String username, LoginUser loginUser) {
        if (username == null || loginUser == null) {
            return;
        }
        if (cache.size() >= maxSize) {
            // 宁可全部失效也不能让内存无界增长：缓存是可重建的数据
            log.warn("登录用户缓存达到上限 {}，整体清空", maxSize);
            cache.clear();
        }
        cache.put(username, new CacheEntry(loginUser.copyForRequest(), expireAt()));
    }

    @Override
    public void evict(String username) {
        if (username != null) {
            cache.remove(username);
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }

    /**
     * 定期清理过期条目（避免长期运行后残留大量已过期对象）。
     */
    @Scheduled(fixedDelay = 600_000L)
    public void cleanExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("清理过期登录用户缓存 {} 条，剩余 {}", removed, cache.size());
        }
    }

    private long expireAt() {
        long ttl = ttlSeconds > 0 ? ttlSeconds : 300L;
        return System.currentTimeMillis() + ttl * 1_000L;
    }

    private record CacheEntry(LoginUser value, long expireAt) {

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
