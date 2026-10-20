package com.example.admin.common.util;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具。
 *
 * <p>安全要求：返回给前端的用户信息必须脱敏
 * （密码绝对不能返回、手机号中间 4 位打码、邮箱 @ 前只保留首字符）。
 *
 * <p>放在 common 而不是 infrastructure：脱敏是"业务规则"（什么叫脱敏、脱到什么程度），
 * 与数据库、缓存、Web 框架都无关。原先放在 infrastructure.util 里，
 * 应用层要用它就得反向依赖基础设施层 —— 这是分层被绕开的典型信号，所以移到这里。
 *
 * <p>实现：复用 Hutool 的 {@link DesensitizedUtil}（成熟且覆盖常见场景），
 * 此处做一层薄封装，统一空值处理与项目内规则。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SensitiveUtils {

    /**
     * 手机号脱敏：138****8000。
     *
     * @param phone 手机号
     * @return 脱敏后的手机号；入参为空返回原值
     */
    public static String phone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return phone;
        }
        return DesensitizedUtil.mobilePhone(phone);
    }

    /**
     * 邮箱脱敏：a***@example.com。
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱；入参为空返回原值
     */
    public static String email(String email) {
        if (StrUtil.isBlank(email)) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String prefix = email.substring(0, atIndex);
        String suffix = email.substring(atIndex);
        // 保留首字符，其余打码（至少 3 个星号，避免过短时仍能推断）
        String maskedPrefix = prefix.charAt(0) + "***";
        return maskedPrefix + suffix;
    }

    /**
     * 用户名脱敏：保留首尾字符。
     *
     * @param username 用户名
     * @return 脱敏后的用户名；入参为空返回原值
     */
    public static String username(String username) {
        if (StrUtil.isBlank(username)) {
            return username;
        }
        // 自行实现：保留首尾字符，中间打码（Hutool 版本间 API 差异较大，避免依赖不稳定方法）
        int length = username.length();
        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return username.charAt(0) + "*";
        }
        return username.charAt(0) + StrUtil.repeat('*', length - 2) + username.charAt(length - 1);
    }

    /**
     * 日志行脱敏：抹掉"看起来像凭据"的内容。
     *
     * <p>为什么要它：日志查看接口原样返回日志正文，而日志里可能出现
     * 验证码、令牌、密码（例如开发模式为便于自测打印的明文验证码）。
     * 一旦有人把这些内容落进日志，再通过一个"只读日志"的接口把它展示出来，
     * 就等于把凭据库开放给了所有有日志查看权限的人 —— 而这个权限往往给得很松。
     *
     * <p>策略是"宁可多抹"：认不出的敏感内容按规则模糊掉，运维排查看的是
     * 时间、级别、堆栈与业务 ID，不需要看到完整的令牌和验证码。
     *
     * @param text 原始文本（通常是单行日志）
     * @return 脱敏后的文本
     */
    public static String maskSecrets(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        String masked = SECRET_KV.matcher(text).replaceAll("$1$2***");
        masked = BEARER.matcher(masked).replaceAll("Bearer ***");
        masked = MOBILE.matcher(masked).replaceAll("$1****$2");
        return EMAIL_TEXT.matcher(masked).replaceAll("$1***@$2");
    }

    /** key=value / key: value 形式的凭据（key 命中敏感词表即抹掉值） */
    private static final Pattern SECRET_KV = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|access[-_]?token|refresh[-_]?token|authorization|"
                    + "api[-_]?key|code|验证码|一次性码)(\\s*[:=]\\s*)([^\\s,;\"'}\\]]{3,})");

    /** Authorization: Bearer xxx */
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]{8,}");

    /** 11 位手机号 */
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");

    /** 邮箱地址 */
    private static final Pattern EMAIL_TEXT = Pattern.compile("([A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]*@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");

    /**
     * IP 脱敏：192.168.*.*。
     *
     * @param ip IP 地址
     * @return 脱敏后的 IP
     */
    public static String ip(String ip) {
        if (StrUtil.isBlank(ip)) {
            return ip;
        }
        int firstDot = ip.indexOf('.');
        int secondDot = ip.indexOf('.', firstDot + 1);
        if (secondDot <= 0) {
            return ip;
        }
        return ip.substring(0, secondDot) + ".*.*";
    }
}
