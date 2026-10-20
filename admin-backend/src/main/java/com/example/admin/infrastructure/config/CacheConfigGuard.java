package com.example.admin.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产环境的缓存配置守卫：选了 Redis 就必须真的连得上。
 *
 * <p>为什么需要它：Spring Data Redis 的连接是<b>惰性</b>的 ——
 * 配错地址时应用照样能启动成功，直到第一个用户点登录才报错。
 * 那种"上线后才发现会话存不进去"的故障最难排查，所以这里在启动阶段就做一次连通性检查。
 *
 * <p>与 {@link VerifyConfigGuard} 同一套思路：<b>配错 = 起不来</b>，而不是带病运行。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("prod")
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "redis")
public class CacheConfigGuard {

    private final StringRedisTemplate redisTemplate;

    /** Redis 地址（仅用于错误信息，不含密码） */
    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private String port;

    /**
     * 启动阶段检查 Redis 连通性。
     *
     * @throws IllegalStateException 连接失败时抛出，终止应用启动
     */
    @PostConstruct
    public void check() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            if (pong == null || pong.isBlank()) {
                throw new IllegalStateException("Redis ping 返回空");
            }
            log.info("Redis 连通性检查通过 {}:{}（会话态存储已启用）", host, port);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "admin.cache.type=redis 但 Redis 连接失败（" + host + ":" + port + "）：" + ex.getMessage()
                            + "。请检查 REDIS_HOST/REDIS_PORT/REDIS_PASSWORD；"
                            + "若确为单机部署，可显式设置 ADMIN_CACHE_TYPE=memory 改用内存存储。", ex);
        }
    }
}
