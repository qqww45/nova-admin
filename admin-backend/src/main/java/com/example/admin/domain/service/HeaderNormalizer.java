package com.example.admin.domain.service;

import com.example.admin.domain.model.mapping.ImportMapping;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 表头归一化与模糊匹配（附录A A.1.2）。
 *
 * <p>归一化步骤（按此顺序，顺序不可颠倒）：
 * <ol>
 *     <li>去掉括号字符（保留括号内文字）—— "备注(订单)" 与 "备注" 必须能区分，
 *         去括号后前者变成"备注订单"，正好精确命中订单备注的别名；</li>
 *     <li>全角转半角 —— 用户从系统里复制出来的表头常带全角字符；</li>
 *     <li>去掉所有空白（空格 / 换行 / 制表符）—— Excel 单元格里常见换行；</li>
 *     <li>转小写 —— 兼容英文表头大小写。</li>
 * </ol>
 *
 * <p>匹配策略：先按归一化结果<b>精确</b>匹配别名，未命中再按"包含"匹配。
 * 精确优先是为了避免"型号"被"客户物料号"之类的长别名误吞。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class HeaderNormalizer {

    /**
     * 括号字符（只去掉括号本身，保留括号内文字）。
     *
     * <p>为什么保留内容：需求写的是"忽略括号内容"，但落到表头上，
     * 若把"备注(订单)"整个括号连同文字一起删掉，它就和明细的"备注"撞在一起无法区分；
     * 只去括号字符后得到"备注订单"，正好精确命中订单备注的别名，两种备注得以区分。
     * 而"客户名称（必填）"这类装饰性括号，精确匹配不到时会走包含匹配兜底，不影响识别。
     */
    private static final Pattern BRACKET = Pattern.compile("[()\\[\\]{}<>（）［］｛｝（）《》〈〉]");

    /** 所有空白字符 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private HeaderNormalizer() {
    }

    /**
     * 归一化表头文本。
     *
     * @param header 原始表头（可为空）
     * @return 归一化结果（空输入返回空串）
     */
    public static String normalize(String header) {
        if (header == null) {
            return "";
        }
        String text = BRACKET.matcher(header).replaceAll("");
        text = toHalfWidth(text);
        text = WHITESPACE.matcher(text).replaceAll("");
        return text.toLowerCase();
    }

    /**
     * 全角转半角（不引入三方库，保证领域层纯净）。
     *
     * @param text 原文
     * @return 半角文本
     */
    public static String toHalfWidth(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u3000') {
                builder.append(' ');
            } else if (c > '\uFF00' && c < '\uFF5F') {
                builder.append((char) (c - 0xFEE0));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * 把表头匹配到系统字段。
     *
     * @param header   原始表头
     * @param mappings 该模块的字段映射列表
     * @return 命中的系统字段名；未命中返回 null
     */
    public static String matchField(String header, List<ImportMapping> mappings) {
        String normalized = normalize(header);
        if (normalized.isEmpty() || mappings == null || mappings.isEmpty()) {
            return null;
        }
        // 第一轮：精确匹配（别名归一化后完全相等）
        for (ImportMapping mapping : mappings) {
            for (String alias : mapping.aliasList()) {
                if (normalized.equals(normalize(alias))) {
                    return mapping.getFieldName();
                }
            }
        }
        // 第二轮：包含匹配（如"客户 PO 号"包含"客户订单号"以外的写法）
        for (ImportMapping mapping : mappings) {
            for (String alias : mapping.aliasList()) {
                String normalizedAlias = normalize(alias);
                if (!normalizedAlias.isEmpty()
                        && (normalized.contains(normalizedAlias) || normalizedAlias.contains(normalized))) {
                    return mapping.getFieldName();
                }
            }
        }
        return null;
    }
}
