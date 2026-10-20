package com.example.admin.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 日期时间反序列化兼容性测试。
 *
 * <p>覆盖了"前端日期控件只给到天"这一真实场景：业务侧的订单日期、出货日期、
 * 对账日期在界面上都是 el-date-picker，产出 {@code yyyy-MM-dd}。
 * 若严格要求 {@code yyyy-MM-dd HH:mm:ss}，这些接口会直接解析失败。
 *
 * @author admin-system
 * @since 2026-01-01
 */
class JacksonLocalDateTimeTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()
            .addDeserializer(LocalDateTime.class, new JacksonConfig.LenientLocalDateTimeDeserializer()));

    @Test
    @DisplayName("仅日期：补零点为当天 00:00")
    void dateOnlyShouldFillZeroTime() throws Exception {
        LocalDateTime value = mapper.readValue("\"2026-10-20\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 10, 20, 0, 0), value);
    }

    @Test
    @DisplayName("统一格式：yyyy-MM-dd HH:mm:ss 原样解析")
    void patternShouldParse() throws Exception {
        LocalDateTime value = mapper.readValue("\"2026-10-20 13:45:06\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 10, 20, 13, 45, 6), value);
    }

    @Test
    @DisplayName("ISO 格式：带 T 的第三方时间也能解析")
    void isoShouldParse() throws Exception {
        LocalDateTime value = mapper.readValue("\"2026-10-20T13:45:06\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 10, 20, 13, 45, 6), value);
    }

    @Test
    @DisplayName("空串与 null：返回 null 而不是抛异常")
    void blankShouldBeNull() throws Exception {
        assertNull(mapper.readValue("\"\"", LocalDateTime.class));
        assertNull(mapper.readValue("null", LocalDateTime.class));
    }

    @Test
    @DisplayName("无法识别的格式：抛出解析异常而不是静默吞掉")
    void invalidShouldThrow() {
        assertThrows(Exception.class, () -> mapper.readValue("\"2026/10/20\"", LocalDateTime.class));
    }
}
