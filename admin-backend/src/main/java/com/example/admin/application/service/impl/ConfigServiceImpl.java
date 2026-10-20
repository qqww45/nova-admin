package com.example.admin.application.service.impl;

import com.example.admin.application.service.ConfigService;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.model.config.ConfigType;
import com.example.admin.domain.repository.ConfigRepository;
import com.example.admin.domain.port.ConfigStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置服务实现：仓储 + 缓存双层架构。
 *
 * <p>读路径：缓存 → 未命中回落仓储并回填缓存。
 * <p>写路径：先写 DB（事务），成功后再刷缓存——保证 DB 与缓存的最终一致性。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final ConfigRepository configRepository;
    private final ConfigStore configStore;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // 读
    // ========================================================================

    @Override
    public String getString(String key, String defaultValue) {
        return getRawValue(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String raw = getRawValue(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("配置 {} 的值 [{}] 不是合法整数，回退默认值 {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = getRawValue(key, null);
        if (raw == null) {
            return defaultValue;
        }
        String s = raw.trim().toLowerCase();
        if (s.equals("true") || s.equals("1") || s.equals("yes")) {
            return true;
        }
        if (s.equals("false") || s.equals("0") || s.equals("no")) {
            return false;
        }
        log.warn("配置 {} 的值 [{}] 不是合法布尔，回退默认值 {}", key, raw, defaultValue);
        return defaultValue;
    }

    @Override
    public <T> T getJson(String key, Class<T> type, T defaultValue) {
        String raw = getRawValue(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(raw, type);
        } catch (JsonProcessingException ex) {
            log.warn("配置 {} 的 JSON 解析失败：{}，回退默认值", key, ex.getMessage());
            return defaultValue;
        }
    }

    @Override
    public String getRawValue(String key) {
        return getRawValue(key, null);
    }

    private String getRawValue(String key, String defaultValue) {
        Optional<ConfigItem> cached = configStore.get(key);
        if (cached.isPresent()) {
            return cached.get().getValue();
        }
        // 缓存未命中：回源 DB 并回填（防启动前或缓存被清空的边界情况）
        Optional<ConfigItem> fromDb = configRepository.findByKey(key);
        fromDb.ifPresent(item -> configStore.put(key, item));
        return fromDb.map(ConfigItem::getValue).orElse(defaultValue);
    }

    @Override
    public ConfigItem getItem(String key) {
        Optional<ConfigItem> cached = configStore.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        Optional<ConfigItem> fromDb = configRepository.findByKey(key);
        fromDb.ifPresent(item -> configStore.put(key, item));
        return fromDb.orElse(null);
    }

    @Override
    public List<ConfigItem> listAll() {
        return configRepository.findAll();
    }

    @Override
    public Map<String, ConfigItem> snapshot() {
        return configStore.snapshot();
    }

    // ========================================================================
    // 写
    // ========================================================================

    @Override
    @Transactional
    public ConfigItem create(ConfigItem item) {
        if (item == null || item.getKey() == null || item.getKey().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置键不能为空");
        }
        if (configRepository.findByKey(item.getKey()).isPresent()) {
            throw new BusinessException(ResultCode.DATA_EXIST, "配置键已存在：" + item.getKey());
        }
        if (item.getType() == null) {
            item = ConfigItem.builder()
                    .key(item.getKey())
                    .value(item.getValue())
                    .type(ConfigType.STRING)
                    .builtin(false)
                    .remark(item.getRemark())
                    .build();
        }
        // 校验值可被 type 解析
        validateValueByType(item);
        ConfigItem saved = configRepository.insert(item);
        configStore.put(saved.getKey(), saved);
        return saved;
    }

    @Override
    @Transactional
    public void update(String key, String newValue, String newRemark) {
        ConfigItem existing = configRepository.findByKey(key)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND, "配置不存在：" + key));
        ConfigItem toUpdate = ConfigItem.builder()
                .id(existing.getId())
                .key(existing.getKey())
                .value(newValue)
                .type(existing.getType())
                .builtin(existing.isBuiltin())
                .remark(newRemark)
                .build();
        validateValueByType(toUpdate);
        configRepository.update(toUpdate);
        // 回填最新值到缓存：用 DB 落库后的时间戳与新值
        ConfigItem fresh = configRepository.findByKey(key).orElse(toUpdate);
        configStore.put(key, fresh);
    }

    @Override
    @Transactional
    public boolean delete(String key) {
        ConfigItem existing = configRepository.findByKey(key)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND, "配置不存在：" + key));
        if (existing.isBuiltin()) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "内置配置不可删除：" + key);
        }
        boolean ok = configRepository.deleteByKey(key);
        if (ok) {
            configStore.evict(key);
        }
        return ok;
    }

    @Override
    public void refreshAll() {
        List<ConfigItem> all = configRepository.findAll();
        configStore.clear();
        for (ConfigItem item : all) {
            configStore.put(item.getKey(), item);
        }
        log.info("已重新加载 {} 条系统配置", configStore.size());
    }

    /**
     * 按类型校验值是否能被正确解析：避免写入时合法、读出时报错。
     */
    private void validateValueByType(ConfigItem item) {
        if (item.getValue() == null || item.getValue().isBlank()) {
            return;
        }
        try {
            switch (item.getType()) {
                case NUMBER -> Double.parseDouble(item.getValue().trim());
                case BOOLEAN -> {
                    String s = item.getValue().trim().toLowerCase();
                    if (!s.equals("true") && !s.equals("false") && !s.equals("1") && !s.equals("0") && !s.equals("yes") && !s.equals("no")) {
                        throw new IllegalArgumentException("不是合法布尔值");
                    }
                }
                case JSON -> objectMapper.readTree(item.getValue());
                case STRING -> { /* nothing */ }
            }
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "配置 " + item.getKey() + " 的值不符合类型 " + item.getType() + "：" + ex.getMessage());
        }
    }
}
