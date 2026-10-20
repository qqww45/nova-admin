package com.example.admin.infrastructure.cache;

import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.port.ConfigStore;

import java.util.Map;
import java.util.Optional;

/**
 * 配置缓存接口（基础设施层抽象）。
 *
 * <p>为什么用接口把缓存抽出来：配置是高频读、低频写的场景，
 * 必须有进程内缓存才不会每次都打数据库。但内存缓存不能跨实例，
 * 上规模后要切到 Redis（多实例共享）。通过接口隔离，切换实现不影响
 * 上层（{@code application.service.ConfigService}）。
 *
 * <p>当前实现：
 * <ul>
 *     <li>{@code InMemoryConfigCache}（默认）—— ConcurrentHashMap，单进程有效；</li>
 *     <li>{@code RedisConfigCache} —— 多实例共享，{@code admin.config.cache=redis} 时启用，
 *         装配后自动接管（内存实现带 {@code @ConditionalOnMissingBean}）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ConfigCache extends ConfigStore {

    /**
     * 取配置项。
     */
    Optional<ConfigItem> get(String key);

    /**
     * 写入或覆盖单条。
     */
    void put(String key, ConfigItem item);

    /**
     * 淘汰单条。
     */
    void evict(String key);

    /**
     * 清空全部。
     */
    void clear();

    /**
     * 当前缓存的全量快照（key → 配置项）。
     */
    Map<String, ConfigItem> snapshot();

    /**
     * 缓存项数量。
     */
    int size();
}
