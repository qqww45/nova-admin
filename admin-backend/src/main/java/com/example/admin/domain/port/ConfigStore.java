package com.example.admin.domain.port;

import com.example.admin.domain.model.config.ConfigItem;

import java.util.Map;
import java.util.Optional;

/**
 * 系统配置存取端口。
 *
 * <p>配置读多写少，天然要缓存；但"缓存"是手段不是业务，应用层不该关心底下是
 * Caffeine、Redis 还是直接查库。这里只声明能力，由基础设施决定怎么存。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ConfigStore {

    /**
     * 读取配置项。
     *
     * @param key 配置键
     * @return 配置项
     */
    Optional<ConfigItem> get(String key);

    /**
     * 写入配置项。
     *
     * @param key  配置键
     * @param item 配置项
     */
    void put(String key, ConfigItem item);

    /**
     * 全量快照。
     *
     * @return 全部配置
     */
    Map<String, ConfigItem> snapshot();

    /**
     * 移除单个配置项。
     *
     * @param key 配置键
     */
    void evict(String key);

    /**
     * 清空全部配置。
     */
    void clear();

    /**
     * 当前缓存条数。
     *
     * @return 条数
     */
    int size();
}
