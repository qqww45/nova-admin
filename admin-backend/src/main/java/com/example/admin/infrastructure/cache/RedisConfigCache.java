package com.example.admin.infrastructure.cache;

import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.model.config.ConfigType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置缓存的 Redis 实现。
 *
 * <p>启用方式：{@code admin.config.cache=redis}。
 * 内存实现带 {@code @ConditionalOnMissingBean}，本类一旦装配即自动接管，业务代码无需改动。
 *
 * <p>为什么现在可以写实现：Redis 依赖已经因会话存储（{@code admin.cache.type=redis}）而存在，
 * 保留一堆 {@code throw new UnsupportedOperationException} 的方法毫无意义 ——
 * 那是个"开关一开就炸"的陷阱，而不是什么预留扩展点。
 *
 * <p>TTL 策略：配置项几乎不变，不设过期（{@code set} 不带 TTL），
 * 一致性靠 {@link #evict} / {@link #clear} 显式失效，与内存实现的行为保持一致。
 *
 * <p>为什么不直接 {@code readValue(json, ConfigItem.class)}：
 * {@link ConfigItem} 是全部字段 final 的值对象（无默认构造器），Jackson 无法直接还原，
 * 这里按字段重建 —— 反序列化是基础设施的细节，不该为此改动领域模型。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.config.cache", havingValue = "redis")
public class RedisConfigCache implements ConfigCache {

    private static final String KEY_PREFIX = "config:sys:";

    private final StringRedisTemplate redis;

    private final ObjectMapper objectMapper;

    @Override
    public Optional<ConfigItem> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String json = redis.opsForValue().get(KEY_PREFIX + key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        return parse(json).or(() -> {
            // 缓存里的内容无法还原（版本升级、手工改过值）：按未命中处理，
            // 让上层回源数据库重建缓存，好过把异常抛到业务链路上。
            log.warn("配置缓存内容无法解析，按未命中处理并回源 key={}", key);
            return Optional.empty();
        });
    }

    @Override
    public void put(String key, ConfigItem item) {
        if (key == null || key.isBlank() || item == null) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + key, objectMapper.writeValueAsString(item));
        } catch (Exception e) {
            log.warn("写入配置缓存失败（下次读取将回源数据库）key={}", key, e);
        }
    }

    @Override
    public void evict(String key) {
        if (key != null && !key.isBlank()) {
            redis.delete(KEY_PREFIX + key);
        }
    }

    @Override
    public void clear() {
        List<String> keys = scanKeys(KEY_PREFIX + "*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Override
    public Map<String, ConfigItem> snapshot() {
        List<String> keys = scanKeys(KEY_PREFIX + "*");
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null) {
            return Map.of();
        }
        Map<String, ConfigItem> result = new HashMap<>();
        for (int i = 0; i < keys.size() && i < values.size(); i++) {
            String json = values.get(i);
            if (json == null || json.isBlank()) {
                continue;
            }
            String cacheKey = keys.get(i).substring(KEY_PREFIX.length());
            parse(json).ifPresent(item -> result.put(cacheKey, item));
        }
        return result;
    }

    @Override
    public int size() {
        // 配置项数量有限（通常几十条），SCAN 计数足够；
        // 将来若配置项上千，应改为维护一个独立的计数 key，而不是每次全量扫描。
        return scanKeys(KEY_PREFIX + "*").size();
    }

    private Optional<ConfigItem> parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null || node.isNull() ? Optional.empty() : Optional.of(toItem(node));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ConfigItem toItem(JsonNode node) {
        return ConfigItem.builder()
                .id(readLong(node, "id"))
                .key(readText(node, "key"))
                .value(readText(node, "value"))
                .type(readType(node))
                .builtin(node.path("builtin").asBoolean(false))
                .remark(readText(node, "remark"))
                .createTime(readTime(node, "createTime"))
                .updateTime(readTime(node, "updateTime"))
                .build();
    }

    private Long readLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private ConfigType readType(JsonNode node) {
        String value = readText(node, "type");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ConfigType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("未知的配置值类型，按 null 处理 type={}", value);
            return null;
        }
    }

    private LocalDateTime readTime(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, LocalDateTime.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 用 SCAN 扫描匹配的 Key（不用 KEYS：数据量大时会阻塞 Redis 主线程）。
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
