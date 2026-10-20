package com.example.admin.infrastructure.persistence.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * 兼容型 {@link LocalDateTime} 类型处理器。
 *
 * <p>为什么需要自定义：
 * SQLite 的 JDBC 驱动对 {@code java.time} 的支持不如 MySQL/PostgreSQL 完善，
 * 不同驱动 {@code getObject} 可能返回 {@code String}、{@code Timestamp}、{@code Long} 等不同类型，
 * 统一在此做归一化处理，保证同一套代码在 SQLite / MySQL / PostgreSQL 上行为一致。
 *
 * <p>写入策略：统一以 {@code yyyy-MM-dd HH:mm:ss} 字符串写入，
 * 该格式是三种数据库都能正确解析的日期字面量，避免使用任何数据库方言函数。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@MappedTypes(LocalDateTime.class)
@MappedJdbcTypes(value = {JdbcType.TIMESTAMP, JdbcType.DATE, JdbcType.TIME, JdbcType.VARCHAR, JdbcType.OTHER})
public class LocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    /** 统一日期时间格式 */
    private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    // 兼容 SQLite 常见的 “2026-01-01 00:00:00.0” 小数秒写法
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .toFormatter();

    /** 与 Jackson 输出格式保持一致 */
    private static final DateTimeFormatter WRITE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 写入参数：以标准字符串写入，跨库通用。
     *
     * @param ps        预编译语句
     * @param i         参数下标
     * @param parameter 参数值
     * @param jdbcType  JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.format(WRITE_FORMATTER));
    }

    /**
     * 按列名读取并转换。
     *
     * @param rs         结果集
     * @param columnName 列名
     * @return 时间值，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return convert(rs.getObject(columnName));
    }

    /**
     * 按列下标读取并转换。
     *
     * @param rs          结果集
     * @param columnIndex 列下标
     * @return 时间值，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return convert(rs.getObject(columnIndex));
    }

    /**
     * 从存储过程输出参数读取并转换。
     *
     * @param cs          存储过程语句
     * @param columnIndex 参数下标
     * @return 时间值，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return convert(cs.getObject(columnIndex));
    }

    /**
     * 将数据库返回的原始值归一化为 {@link LocalDateTime}。
     *
     * @param value 原始值（可能是 LocalDateTime / Timestamp / String / Long）
     * @return 归一化后的时间，无法识别时返回 null
     */
    private LocalDateTime convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.sql.Date) {
            return LocalDateTime.ofInstant(((java.sql.Date) value).toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof Long) {
            // 毫秒时间戳
            return LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) value), ZoneId.systemDefault());
        }
        String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, FORMATTER);
        } catch (Exception e) {
            // 兜底：交给 java.time 默认解析（可处理 ISO-8601 的 T 分隔写法）
            try {
                return LocalDateTime.parse(text.replace(' ', 'T'));
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
