package com.example.admin.infrastructure.cache;

import com.example.admin.domain.model.config.ConfigItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置缓存的内存实现（默认）。
 *
 * <p>特点：
 * <ul>
 *     <li>用 {@link ConcurrentHashMap}：读无锁、写入线程安全；</li>
 *     <li>无 TTL：配置项本身就是"几乎不变"的数据，启动时全量加载即可；</li>
 *     <li>{@link ConditionalOnMissingBean}：当存在 {@code RedisConfigCache} 等其它实现时不生效，
 *         切缓存只需切换 Bean 装配，不需要改业务代码。</li>
 * </ul>
 *
 * <p>不适用场景：多实例部署时各实例缓存独立，修改配置需要 {@code ConfigService.refreshAll()}
 * 或重启所有实例。切到 Redis 实现可解决。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
@ConditionalOnMissingBean(value = ConfigCache.class, ignored = InMemoryConfigCache.class)
public class InMemoryConfigCache implements ConfigCache {

    private final Map<String, ConfigItem> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ConfigItem> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void put(String key, ConfigItem item) {
        if (key != null && item != null) {
            store.put(key, item);
        }
    }

    @Override
    public void evict(String key) {
        store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public Map<String, ConfigItem> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(store));
    }

    @Override
    public int size() {
        return store.size();
    }
}
