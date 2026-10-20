package com.example.admin.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 生产环境 JWT 密钥守卫。
 *
 * <p>为什么单独做这道检查：密钥长度 {@code >= 32} 只是 HS256 的<b>算法要求</b>，
 * 满足它并不代表密钥安全 —— 仓库里那个"长度刚好合规、内容完全公开"的默认串
 * 就能顺利通过长度校验。只要部署时漏设 {@code JWT_SECRET}，任何人都能用它
 * 签出一张超管令牌（{@code admin} 角色持有 {@code *:*:*} 通配权限），
 * 风控、票据、数据权限会一起失效。
 *
 * <p>因此这里在生产启动时额外检查三件事：非空、不是已知默认串、不是低熵串。
 * 沿用 {@code VerifyConfigGuard} 的策略：<b>配错就起不来</b>，而不是带病运行。
 *
 * <p>注意：日志只打印长度，绝不打印密钥本身。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
// staging 同样要求强密钥：过渡环境也在公网，用默认密钥等于把超管权限公开
@Profile("prod | staging")
public class JwtSecretGuard {

    /** 仓库中的默认密钥（必须与 application.yml 里的兜底值一致） */
    private static final String DEFAULT_SECRET =
            "admin-system-default-jwt-secret-please-change-in-production-32bytes";

    /** 一眼可见的弱密钥 */
    private static final Set<String> WEAK_SECRETS =
            Set.of(DEFAULT_SECRET, "secret", "changeit", "changeme", "password", "123456");

    /** HS256 要求的最小密钥长度（字节） */
    private static final int MIN_LENGTH = 32;

    /** 低熵判定阈值：不同字符数少于该值视为弱密钥 */
    private static final int MIN_DISTINCT_CHARS = 8;

    @Value("${admin.security.jwt-secret:}")
    private String secret;

    /**
     * 校验密钥强度。
     *
     * @throws IllegalStateException 密钥缺失、为默认值或熵过低时抛出，终止启动
     */
    @PostConstruct
    public void check() {
        String value = secret == null ? "" : secret.trim();
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "生产环境必须通过环境变量 JWT_SECRET 注入 JWT 密钥（建议 48 字节以上随机串，"
                            + "可用 openssl rand -base64 48 生成）");
        }
        if (WEAK_SECRETS.contains(value)) {
            throw new IllegalStateException(
                    "检测到默认 JWT 密钥，生产环境严禁使用。请通过环境变量 JWT_SECRET 注入随机密钥");
        }
        if (value.length() < MIN_LENGTH) {
            throw new IllegalStateException(
                    "JWT 密钥长度不足 " + MIN_LENGTH + " 字节（当前 " + value.length() + "），HS256 要求至少 32 字节");
        }
        long distinct = value.chars().distinct().count();
        if (distinct < MIN_DISTINCT_CHARS) {
            throw new IllegalStateException(
                    "JWT 密钥熵过低（仅 " + distinct + " 种不同字符），疑似人为构造的弱密钥，请更换为随机串");
        }
        log.info("JWT 密钥校验通过（长度 {} 字节，{} 种字符）", value.length(), distinct);
    }
}
