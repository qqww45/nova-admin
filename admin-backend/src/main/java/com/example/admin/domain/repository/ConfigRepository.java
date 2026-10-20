package com.example.admin.domain.repository;

import com.example.admin.domain.model.config.ConfigItem;

import java.util.List;
import java.util.Optional;

/**
 * 配置仓储接口（领域层定义）。
 *
 * <p>实现交由基础设施层（MyBatis-Plus + 数据库），业务代码只依赖此接口。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ConfigRepository {

    /**
     * 按 key 查询。
     *
     * @param key 配置键
     * @return 配置项
     */
    Optional<ConfigItem> findByKey(String key);

    /**
     * 列出全部配置（按 key 排序）。
     *
     * @return 配置项列表
     */
    List<ConfigItem> findAll();

    /**
     * 新增配置。
     *
     * @param item 配置项
     * @return 带主键的配置项
     */
    ConfigItem insert(ConfigItem item);

    /**
     * 按 key 更新值与备注。
     *
     * @param item 配置项
     */
    void update(ConfigItem item);

    /**
     * 按 key 删除（非内置项才能删）。
     *
     * @param key 配置键
     * @return 是否删除成功
     */
    boolean deleteByKey(String key);
}
