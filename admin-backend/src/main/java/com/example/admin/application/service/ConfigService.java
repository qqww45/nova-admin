package com.example.admin.application.service;

import com.example.admin.domain.model.config.ConfigItem;

import java.util.List;
import java.util.Map;

/**
 * 应用层配置服务：编排仓储与缓存，对外暴露类型安全的取值 API。
 *
 * <p>详见 {@link com.example.admin.domain.service.ConfigService} 注释。
 *
 * <p>本接口位于应用层而非领域层，是因为它依赖了 {@code ConfigCache}（基础设施抽象）。
 * 严格分层下，领域层不依赖任何基础设施；这里把"协调仓储 + 缓存"这件事视为应用用例。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ConfigService {

    // ---- 读 ----
    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    <T> T getJson(String key, Class<T> type, T defaultValue);

    String getRawValue(String key);

    ConfigItem getItem(String key);

    List<ConfigItem> listAll();

    Map<String, ConfigItem> snapshot();

    // ---- 写 ----
    ConfigItem create(ConfigItem item);

    void update(String key, String newValue, String newRemark);

    boolean delete(String key);

    void refreshAll();
}
