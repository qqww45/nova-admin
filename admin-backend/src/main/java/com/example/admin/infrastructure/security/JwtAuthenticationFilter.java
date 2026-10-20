package com.example.admin.infrastructure.security;

import com.example.admin.common.constant.Constants;
import com.example.admin.domain.model.auth.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器（Infrastructure 层）：把请求头中的 Bearer Token 转换为安全上下文。
 *
 * <p>处理流程：
 * <ol>
 *     <li>从 {@code Authorization} 头提取令牌；</li>
 *     <li>解析令牌并校验类型必须为 access（禁止用 refresh 令牌调用业务接口）；</li>
 *     <li>查询黑名单：已登出的令牌直接拒绝；</li>
 *     <li>加载用户与权限，写入 {@code SecurityContext}，供 {@code @PreAuthorize} 使用。</li>
 * </ol>
 *
 * <p>异常处理：令牌无效/过期/被拉黑时清空上下文，
 * 交由 {@code AuthenticationEntryPoint} 统一返回 401 JSON，保证前端响应结构一致。
 *
 * <p>性能说明：每次请求加载一次权限，保证角色变更即时生效；
 * 访问量增大时可引入缓存（以 username 为 key，角色变更时失效）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private final TokenBlacklistService tokenBlacklistService;

    private final UserDetailsService userDetailsService;

    /**
     * 过滤处理。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtTokenProvider.parse(token);
                String type = claims.get(Constants.CLAIM_TYPE, String.class);
                if (!Constants.TOKEN_TYPE_ACCESS.equals(type)) {
                    throw new JwtException("令牌类型不正确，禁止使用刷新令牌访问业务接口");
                }
                String jti = claims.getId();
                if (tokenBlacklistService.contains(jti)) {
                    throw new JwtException("令牌已失效（已登出）");
                }
                String username = claims.getSubject();
                AuthenticatedUser authenticatedUser = (AuthenticatedUser) userDetailsService.loadUserByUsername(username);
                LoginUser loginUser = authenticatedUser.getLoginUser();
                loginUser.setTokenId(jti);
                // 把请求里的设备标识带进登录上下文：登出时前端若不传 refreshToken（该参数是可选的），
                // 服务端仍要能按设备清理 refresh 令牌与在线会话，否则"退出登录"只是假象。
                // 取的是前端每请求都带的 X-Device-Id 头，它只用于定位"要清理哪台设备"，
                // 不参与任何鉴权判定；真正的设备校验走 refresh 令牌里不可伪造的 deviceId claim。
                loginUser.setDeviceId(request.getHeader("X-Device-Id"));

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        authenticatedUser, null, authenticatedUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                // 令牌异常：清空上下文，交由 EntryPoint 返回 401
                SecurityContextHolder.clearContext();
                log.debug("JWT 校验失败：{}", e.getMessage());
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                log.error("加载登录用户信息失败", e);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取令牌。
     *
     * @param request 请求
     * @return 令牌字符串；无令牌或格式不符返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            header = request.getHeader(Constants.TOKEN_HEADER);
        }
        if (header == null || !header.startsWith(Constants.TOKEN_PREFIX)) {
            return null;
        }
        String token = header.substring(Constants.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
