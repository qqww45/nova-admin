package com.example.admin.infrastructure.security;

import cn.hutool.core.codec.Base64;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌工具（签发 / 解析 / 校验）。
 *
 * <p>设计要点：
 * <ul>
 *     <li>使用 jjwt 0.12.x 新版 API（{@code Jwts.parser().verifyWith(key)}），
 *         旧版 0.9.x 的 {@code parser().setSigningKey()} 已废弃且存在安全问题，禁用；</li>
 *     <li>令牌分为 access（2 小时）与 refresh（7 天）两类，通过 {@code type} 声明区分，
 *         避免刷新令牌被当作访问令牌使用（越权风险）；</li>
 *     <li>每个令牌携带 {@code jti}，用于登出时精准拉黑单个令牌；</li>
 *     <li>密钥由配置项 / 环境变量注入，禁止硬编码；HS256 要求密钥长度 >= 32 字节。</li>
 * </ul>
 *
 * <p>安全：本类所有方法都不会打印令牌内容，避免令牌泄漏到日志。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** JWT 密钥（Base64/原始字符串均可，需 >= 32 字节） */
    @Value("${admin.security.jwt-secret}")
    private String secret;

    /** Access Token 有效期（秒） */
    @Value("${admin.security.access-token-ttl}")
    private long accessTokenTtl;

    /** Refresh Token 有效期（秒） */
    @Value("${admin.security.refresh-token-ttl}")
    private long refreshTokenTtl;

    /** 签名密钥（HS256） */
    private SecretKey key;

    /**
     * 初始化签名密钥。
     *
     * <p>为什么用 {@code @PostConstruct}：密钥解析只需一次，
     * 避免每次签发都重复做字节转换与密钥校验。
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // 密钥过短会导致 HS256 签名强度不足，快速失败比带着隐患运行更好
            throw new IllegalStateException("JWT 密钥长度不足 32 字节，请通过环境变量 JWT_SECRET 配置更安全的密钥");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT 初始化完成，accessTokenTtl={}s, refreshTokenTtl={}s", accessTokenTtl, refreshTokenTtl);
    }

    /**
     * 生成 Access Token。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return 令牌字符串
     */
    public String generateAccessToken(Long userId, String username) {
        return buildToken(userId, username, com.example.admin.common.constant.Constants.TOKEN_TYPE_ACCESS, accessTokenTtl);
    }

    /**
     * 生成 Refresh Token。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return 刷新令牌字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        return generateRefreshToken(userId, username, null);
    }

    /**
     * 生成 Refresh Token（携带设备标识）。
     *
     * <p>为什么要把 deviceId 写进令牌：刷新时不依赖前端传参，
     * 服务端可直接从令牌取出设备维度去 {@code refresh_token:{userId}:{deviceId}} 校验，
     * 既少一个可被篡改的输入，也让"按设备踢人"有据可依。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param deviceId 设备标识
     * @return 刷新令牌字符串
     */
    public String generateRefreshToken(Long userId, String username, String deviceId) {
        return buildToken(userId, username, com.example.admin.common.constant.Constants.TOKEN_TYPE_REFRESH,
                refreshTokenTtl, deviceId);
    }

    /**
     * 构造 JWT。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param type     令牌类型（access / refresh）
     * @param ttl      有效期（秒）
     * @return 令牌字符串
     */
    private String buildToken(Long userId, String username, String type, long ttl) {
        return buildToken(userId, username, type, ttl, null);
    }

    /**
     * 构造 JWT（可携带设备标识）。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param type     令牌类型（access / refresh）
     * @param ttl      有效期（秒）
     * @param deviceId 设备标识（可为空）
     * @return 令牌字符串
     */
    private String buildToken(Long userId, String username, String type, long ttl, String deviceId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.plusSeconds(ttl);
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                // jti：令牌唯一标识，登出时写入黑名单即可精准失效
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(com.example.admin.common.constant.Constants.CLAIM_USER_ID, userId)
                .claim(com.example.admin.common.constant.Constants.CLAIM_TYPE, type)
                .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(Date.from(expire.atZone(ZoneId.systemDefault()).toInstant()));
        if (deviceId != null && !deviceId.isBlank()) {
            builder.claim(com.example.admin.common.constant.Constants.CLAIM_DEVICE_ID, deviceId);
        }
        return builder.signWith(key).compact();
    }

    /**
     * 获取令牌中的设备标识（refresh 令牌才携带）。
     *
     * @param token 令牌
     * @return 设备标识，可能为 null
     */
    public String getDeviceId(String token) {
        try {
            return parse(token).get(com.example.admin.common.constant.Constants.CLAIM_DEVICE_ID, String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * 解析并校验令牌。
     *
     * @param token 令牌字符串
     * @return 载荷
     * @throws JwtException 令牌无效/过期/签名错误时抛出
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取令牌唯一标识 jti。
     *
     * @param token 令牌
     * @return jti
     */
    public String getJti(String token) {
        return parse(token).getId();
    }

    /**
     * 获取令牌类型。
     *
     * @param token 令牌
     * @return access / refresh
     */
    public String getTokenType(String token) {
        return parse(token).get(com.example.admin.common.constant.Constants.CLAIM_TYPE, String.class);
    }

    /**
     * 获取令牌中的用户 ID。
     *
     * @param token 令牌
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        Integer userId = parse(token).get(com.example.admin.common.constant.Constants.CLAIM_USER_ID, Integer.class);
        return userId == null ? null : userId.longValue();
    }

    /**
     * 获取令牌过期时间。
     *
     * @param token 令牌
     * @return 过期时间
     */
    public LocalDateTime getExpiration(String token) {
        Date expiration = parse(token).getExpiration();
        return LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 判断令牌是否已过期。
     *
     * @param token 令牌
     * @return true 表示已过期
     */
    public boolean isExpired(String token) {
        try {
            parse(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            // 其他解析失败统一视为不可用
            return true;
        }
    }

    /**
     * 校验令牌类型是否符合预期（防止用 refresh 令牌调用业务接口）。
     *
     * @param token         令牌
     * @param expectedType  期望类型
     * @return 是否匹配
     */
    public boolean matchesType(String token, String expectedType) {
        try {
            return expectedType.equals(getTokenType(token));
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Access Token 有效期（秒），用于响应体告知前端过期时间。
     *
     * @return 有效期秒数
     */
    public long getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Refresh Token 有效期（秒）。
     *
     * @return 有效期秒数
     */
    public long getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    /**
     * 将密钥做掩码输出，仅用于启动自检日志（严禁输出真实密钥）。
     *
     * @return 掩码后的密钥指纹
     */
    public String secretFingerprint() {
        return Base64.encode(secret.substring(0, 4) + "****");
    }
}
