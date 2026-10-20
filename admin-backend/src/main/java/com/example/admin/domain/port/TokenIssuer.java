package com.example.admin.domain.port;

import com.example.admin.domain.exception.InvalidTokenException;

/**
 * 令牌签发与解析端口。
 *
 * <p>存在理由同 {@link OperatorContext}：签发 JWT、解析 JWT 都是技术细节，
 * 应用层只需要"给我一个令牌"和"这个令牌说的是什么"。把 JJWT 的类型挡在领域层之外，
 * 应用层就不用再 import {@code io.jsonwebtoken.*}。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface TokenIssuer {

    /**
     * 签发访问令牌。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return 访问令牌
     */
    String generateAccessToken(Long userId, String username);

    /**
     * 签发刷新令牌。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param deviceId 设备标识
     * @return 刷新令牌
     */
    String generateRefreshToken(Long userId, String username, String deviceId);

    /**
     * 解析令牌（签名校验与过期判定都在这里完成）。
     *
     * @param token 令牌
     * @return 令牌内容
     * @throws InvalidTokenException 令牌无效、过期或格式非法
     */
    TokenClaims parse(String token) throws InvalidTokenException;

    /**
     * 取令牌唯一标识。
     *
     * @param token 令牌
     * @return jti
     */
    String jtiOf(String token);

    /**
     * 访问令牌有效期。
     *
     * @return 秒数
     */
    long accessTokenTtlSeconds();

    /**
     * 刷新令牌有效期。
     *
     * @return 秒数
     */
    long refreshTokenTtlSeconds();
}
