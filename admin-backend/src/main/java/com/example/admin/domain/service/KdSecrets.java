package com.example.admin.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 采集器令牌 / 绑定码生成与摘要。
 *
 * <p>约定（方案 §5）：
 * <ul>
 *     <li>机器令牌 32 字节随机，DB 只存 SHA-256 摘要，支持吊销与重新签发；</li>
 *     <li>绑定码 8 位、去掉易混字符（0/O/1/I），5 分钟有效、一次一用。</li>
 * </ul>
 *
 * <p>用 SHA-256 而不是 BCrypt：令牌是 32 字节高熵随机串，不存在被爆破的口令空间，
 * 每次请求都要校验，慢哈希会把鉴权成本放大到不可接受。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public final class KdSecrets {

    /** 绑定码字符集：去掉 0/O/1/I/L 等易混字符，方便电话口述 */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    /** 绑定码长度 */
    private static final int CODE_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private KdSecrets() {
        // 工具类不允许实例化
    }

    /**
     * 生成机器令牌（32 字节，URL 安全 Base64）。
     *
     * @return 令牌原文，只返回给客户端一次
     */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 生成一次性绑定码。
     *
     * @return 8 位大写绑定码
     */
    public static String newBindCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /**
     * 计算 SHA-256 摘要（十六进制小写）。
     *
     * @param raw 原文
     * @return 64 位十六进制摘要
     */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，走到这里说明运行环境异常
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    /**
     * 绑定码归一化：去空格、转大写，避免用户输入小写或带空格导致"码无效"。
     *
     * @param code 用户输入
     * @return 归一化后的码
     */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("\\s+", "").toUpperCase();
    }
}
