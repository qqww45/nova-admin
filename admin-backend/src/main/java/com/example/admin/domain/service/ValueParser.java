package com.example.admin.domain.service;

import com.example.admin.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 单元格值解析器（附录A A.3 / A.4 的取值规则）。
 *
 * <p>要容忍的输入（历史台账的真实脏数据）：
 * <ul>
 *     <li>数值："1,234.5"（千分位）、"1234.5 "（多余空格）、"１２３"（全角）；</li>
 *     <li>税率："13%" / "0.13" / "13" / 空 —— 空与 0 都表示不含税；</li>
 *     <li>日期："2026-01-05" / "2026/1/5" / "2026.1.5" / Excel 日期序列（如 45000）。</li>
 * </ul>
 *
 * <p>解析失败统一抛 {@link BusinessException}，消息可直接进《错误清单 xlsx》的"错误原因"列。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class ValueParser {

    /** 千分位分隔符 */
    private static final Pattern THOUSANDS = Pattern.compile(",");

    /** 非数值字符（用于剔除单位后缀，如 "100 pcs"） */
    private static final Pattern NON_NUMBER = Pattern.compile("[^0-9.+\\-]");

    /** 简单日期格式候选（按尝试顺序） */
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
    };

    /** Excel 日期序列基准：1900 日期系统中，1 = 1900-01-01，且存在 1900-02-29 的历史 bug，故用 1899-12-30 */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private ValueParser() {
    }

    /**
     * 解析数值（数量 / 单价）。
     *
     * @param raw   原始文本
     * @param label 用于提示的字段名
     * @return 数值；空输入返回 null（由调用方决定是否套用默认值）
     * @throws BusinessException 无法解析为数字
     */
    public static BigDecimal toNumber(String raw, String label) {
        String text = clean(raw);
        if (text.isEmpty()) {
            return null;
        }
        text = THOUSANDS.matcher(text).replaceAll("");
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            // 兜底：剔除单位等非数值字符后再试一次（"100 pcs" → 100）
            String stripped = NON_NUMBER.matcher(text).replaceAll("");
            if (!stripped.isEmpty()) {
                try {
                    return new BigDecimal(stripped);
                } catch (NumberFormatException ignored) {
                    // 落到下面的统一报错
                }
            }
            throw new BusinessException(label + " 不是合法数字：" + raw);
        }
    }

    /**
     * 解析税率。
     *
     * <p>约定："13%" / "13" 视为 0.13；"0.13" 视为 0.13（值 &gt; 1 时判定为百分数写法）；
     * 空或 0 视为不含税。
     *
     * @param raw   原始文本
     * @param label 用于提示的字段名
     * @return 税率（0.13 表示 13%）；空输入返回 null
     * @throws BusinessException 无法解析为税率
     */
    public static BigDecimal toRate(String raw, String label) {
        String text = clean(raw);
        if (text.isEmpty()) {
            return null;
        }
        boolean percent = text.endsWith("%") || text.endsWith("％");
        String numeric = percent ? text.substring(0, text.length() - 1) : text;
        BigDecimal value;
        try {
            value = new BigDecimal(THOUSANDS.matcher(numeric.trim()).replaceAll(""));
        } catch (NumberFormatException e) {
            throw new BusinessException(label + " 不是合法税率：" + raw);
        }
        BigDecimal rate = percent || value.compareTo(BigDecimal.ONE) > 0
                ? value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                : value.setScale(4, RoundingMode.HALF_UP);
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(label + " 不能为负数：" + raw);
        }
        return rate;
    }

    /**
     * 解析日期为当天零点。
     *
     * @param raw   原始文本
     * @param label 用于提示的字段名
     * @return 日期时间（00:00:00）；空输入返回 null
     * @throws BusinessException 无法解析为日期，或日期超出合理区间
     */
    public static LocalDateTime toDate(String raw, String label) {
        String text = clean(raw);
        if (text.isEmpty()) {
            return null;
        }
        // Excel 日期序列（纯数字且不带分隔符）
        if (text.matches("\\d+(\\.\\d+)?")) {
            long serial = new BigDecimal(text).longValue();
            if (serial > 0 && serial < 100000) {
                LocalDate date = EXCEL_EPOCH.plusDays(serial);
                return LocalDateTime.of(date, LocalTime.MIN);
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.of(LocalDate.parse(text, formatter), LocalTime.MIN);
            } catch (DateTimeParseException ignored) {
                // 换下一种格式
            }
        }
        try {
            return LocalDateTime.parse(text.replace('/', '-').replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            throw new BusinessException(label + " 不是合法日期（支持 yyyy-MM-dd / yyyy/M/d / Excel 日期序列）：" + raw);
        }
    }

    /**
     * 清洗原始文本：全角转半角 + 去首尾空白。
     *
     * @param raw 原始文本
     * @return 清洗结果（空输入返回空串）
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return HeaderNormalizer.toHalfWidth(raw).trim();
    }

    /**
     * 文本值：清洗后为空返回 null（便于统一"空 = 不填"语义）。
     *
     * @param raw 原始文本
     * @return 清洗后的文本，空返回 null
     */
    public static String toText(String raw) {
        String text = clean(raw);
        return text.isEmpty() ? null : text;
    }
}
