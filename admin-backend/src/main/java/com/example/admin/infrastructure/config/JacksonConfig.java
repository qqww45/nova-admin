package com.example.admin.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.TimeZone;

/**
 * Jackson 序列化配置。
 *
 * <p>三条关键约定：
 * <ul>
 *     <li>时间统一 {@code yyyy-MM-dd HH:mm:ss}（GMT+8），前后端零歧义；</li>
 *     <li>{@code Long} 包装类型序列化为字符串：雪花算法生成的 ID 为 19 位，
 *         超过 JavaScript 安全整数（2^53-1），直接输出数字会导致前端精度丢失（末位变 0）；
 *         注意此处只注册 {@code Long.class} 而不注册 {@code long}，
 *         因此分页的 total/pages 等基本类型仍以数字输出，前端类型保持准确；</li>
 *     <li>统一 Jackson，禁止引入 Fastjson（历史安全漏洞较多）。</li>
 * </ul>
 *
 * <p>反序列化侧额外放宽了一点：<b>只到天的日期也能反序列化为 LocalDateTime</b>。
 * 原因是业务侧的"下单时间/出货日期/对账日期"在界面上就是日期控件（el-date-picker），
 * 天然只产出 {@code yyyy-MM-dd}；若严格要求 {@code yyyy-MM-dd HH:mm:ss}，
 * 前端要么在每个日期字段上补 {@code 00:00:00}，要么一次保存失败 —— 两种都是把格式负担转嫁给调用方。
 * 这里按"完整格式 → ISO → 仅日期"依次尝试，缺失的时间部分补零点，
 * 序列化格式不变（仍为 {@code yyyy-MM-dd HH:mm:ss}），对既有接口无影响。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Configuration
public class JacksonConfig {

    /** 统一日期时间格式 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 仅日期格式（日期控件产出，反序列化时补零点） */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /**
     * 定制 ObjectMapper。
     *
     * @return Jackson 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());

        return builder -> {
            builder.simpleDateFormat(DATE_TIME_PATTERN);
            builder.timeZone(TimeZone.getTimeZone("GMT+8"));
            builder.modules(javaTimeModule);
            // 雪花 ID 前端精度保护（仅包装类型 Long）
            builder.serializerByType(Long.class, ToStringSerializer.instance);
        };
    }

    /**
     * 兼容"只到天"的 LocalDateTime 反序列化器（仅反序列化，序列化仍走统一格式）。
     *
     * <p>包内可见是为了让单测能直接构造并验证三种输入，不必拉起 Spring 容器。
     */
    static final class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            // 1) 统一格式：yyyy-MM-dd HH:mm:ss
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(DATE_TIME_PATTERN));
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            // 2) ISO：yyyy-MM-ddTHH:mm:ss（第三方系统对接常见）
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
            // 3) 仅日期：yyyy-MM-dd，时间补零点
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(DATE_PATTERN)).atStartOfDay();
            } catch (DateTimeParseException e) {
                return context.reportInputMismatch(LocalDateTime.class,
                        "日期时间格式不正确：%s，应为 %s 或 %s", value, DATE_TIME_PATTERN, DATE_PATTERN);
            }
        }
    }
}
