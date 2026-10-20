package com.example.admin.domain.model.config;

/**
 * 配置项值类型。
 *
 * <p>不同类型决定 {@link com.example.admin.domain.service.ConfigService} 的取值方法：
 * <ul>
 *     <li>{@link #STRING}  —— {@code getString}</li>
 *     <li>{@link #NUMBER}  —— {@code getInt} / {@code getLong} / {@code getDouble}</li>
 *     <li>{@link #BOOLEAN} —— {@code getBoolean}</li>
 *     <li>{@link #JSON}    —— {@code getJson}，值必须为合法 JSON 字符串</li>
 * </ul>
 *
 * <p>为什么用枚举而不是字符串：避免存储"string"/"String"/"varchar"这种自由文本；
 * 后续若要新增类型必须改这里，影响面清晰可见。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public enum ConfigType {

    /** 字符串 */
    STRING,

    /** 数值（整数或浮点，按需解析） */
    NUMBER,

    /** 布尔 */
    BOOLEAN,

    /** JSON 字符串 */
    JSON
}
