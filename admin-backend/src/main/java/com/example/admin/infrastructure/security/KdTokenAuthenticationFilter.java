package com.example.admin.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 采集器令牌认证过滤器。
 *
 * <p>只认请求头 {@code X-Kd-Token}。与 {@link JwtAuthenticationFilter} 并存：
 * 采集器令牌走本过滤器，后台 JWT 走 JWT 过滤器，两者互不越权
 * （令牌身份只有 {@code ROLE_KD_CLIENT}，没有后台菜单权限）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@RequiredArgsConstructor
public class KdTokenAuthenticationFilter extends OncePerRequestFilter {

    /** 采集器令牌请求头 */
    public static final String TOKEN_HEADER = "X-Kd-Token";

    /** 采集器角色：仅用于表达"这是一个合法采集器"，细粒度权限不在这里判 */
    public static final String ROLE_KD_CLIENT = "ROLE_KD_CLIENT";

    private final KdTokenService kdTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(TOKEN_HEADER);
        if (token != null && !token.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<KdPrincipal> principal = kdTokenService.authenticate(token);
            // 校验失败不抛异常：交给 AuthenticationEntryPoint 统一返回 401 JSON，
            // 采集器据 status=401 提示"令牌已失效，请重新绑定"，而不是解析 HTML 错误页
            principal.ifPresent(p -> {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        p, null, AuthorityUtils.createAuthorityList(ROLE_KD_CLIENT));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}
