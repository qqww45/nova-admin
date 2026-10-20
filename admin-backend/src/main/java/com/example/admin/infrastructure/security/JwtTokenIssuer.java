package com.example.admin.infrastructure.security;

import com.example.admin.common.constant.Constants;
import com.example.admin.domain.exception.InvalidTokenException;
import com.example.admin.domain.port.TokenClaims;
import com.example.admin.domain.port.TokenIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TokenIssuer} 的 JJWT 实现：把 JWT 库的类型挡在应用层之外。
 *
 * <p>这里是"翻译层"：JJWT 的 {@link Claims} 在这里就被转成 {@link TokenClaims}，
 * 它的 {@link JwtException} 被转成领域异常 {@link InvalidTokenException}。
 * 应用层只见端口，换签名库不会波及任何一个用例。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public String generateAccessToken(Long userId, String username) {
        return jwtTokenProvider.generateAccessToken(userId, username);
    }

    @Override
    public String generateRefreshToken(Long userId, String username, String deviceId) {
        return jwtTokenProvider.generateRefreshToken(userId, username, deviceId);
    }

    @Override
    public TokenClaims parse(String token) throws InvalidTokenException {
        try {
            Claims claims = jwtTokenProvider.parse(token);
            return new TokenClaims(
                    claims.getId(),
                    claims.getSubject(),
                    claims.get(Constants.CLAIM_TYPE, String.class),
                    jwtTokenProvider.getDeviceId(token)
            );
        } catch (JwtException e) {
            throw new InvalidTokenException(e.getMessage(), e);
        }
    }

    @Override
    public String jtiOf(String token) {
        return jwtTokenProvider.getJti(token);
    }

    @Override
    public long accessTokenTtlSeconds() {
        return jwtTokenProvider.getAccessTokenTtl();
    }

    @Override
    public long refreshTokenTtlSeconds() {
        return jwtTokenProvider.getRefreshTokenTtl();
    }
}
