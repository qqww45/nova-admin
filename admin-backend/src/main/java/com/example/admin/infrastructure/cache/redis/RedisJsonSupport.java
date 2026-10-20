package com.example.admin.infrastructure.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis JSON 序列化辅助：把领域对象 → JSON 字符串。
 *
 * <p>为什么单独抽一个工具而不是每个类自己 try/catch：
 * 五个 Redis 实现都用相同的"序列化失败 → 异常"逻辑，抽出后异常消息统一，
 * 也避免每个类都依赖 jackson 异常类型。
 *
 * @author admin-system
 * @since 2026-01-01
 */
final class RedisJsonSupport {

    private RedisJsonSupport() {
    }

    /**
     * 对象序列化为 JSON 字符串。
     *
     * @param mapper Jackson ObjectMapper（Spring Boot 默认已包含 JavaTimeModule）
     * @param value  待序列化对象
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败时抛出
     */
    static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Redis 序列化失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * JSON 字符串反序列化为对象。
     *
     * @param mapper Jackson ObjectMapper
     * @param json   JSON 字符串（可为 null）
     * @param type   目标类型
     * @param <T>    类型参数
     * @return 反序列化结果；json 为 null 时返回 null
     * @throws IllegalStateException 反序列化失败时抛出
     */
    static <T> T read(ObjectMapper mapper, String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Redis 反序列化失败：" + ex.getMessage(), ex);
        }
    }
}
