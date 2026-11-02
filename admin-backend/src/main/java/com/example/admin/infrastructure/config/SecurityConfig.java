package com.example.admin.infrastructure.config;

import com.example.admin.infrastructure.security.AccessDeniedHandlerImpl;
import com.example.admin.infrastructure.security.AuthenticationEntryPointImpl;
import com.example.admin.infrastructure.security.JwtAuthenticationFilter;
import com.example.admin.infrastructure.security.JwtTokenProvider;
import com.example.admin.infrastructure.security.KdTokenAuthenticationFilter;
import com.example.admin.infrastructure.security.KdTokenService;
import com.example.admin.infrastructure.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Spring Security 配置：认证、鉴权、会话与安全响应头。
 *
 * <p>关键决策与理由：
 * <ul>
 *     <li><b>关闭 CSRF</b>：前后端分离 + JWT（令牌放在 Authorization 头，不由浏览器自动携带），
 *         不存在 Cookie 自动提交带来的 CSRF 风险，故显式关闭并在注释中说明原因；</li>
 *     <li><b>无状态会话</b>：{@code SessionCreationPolicy.STATELESS}，服务端不保存会话，
 *         便于水平扩展；登录态完全由 JWT 承载；</li>
 *     <li><b>白名单最小化</b>：仅放通登录、验证码、刷新令牌、接口文档与监控健康检查，
 *         其余接口一律需认证；</li>
 *     <li><b>方法级鉴权</b>：由启动类的 {@code @EnableMethodSecurity} 开启，
 *         Controller 上使用 {@code @PreAuthorize("@ss.hasPermi('xxx')")} 做接口级权限控制。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 无需认证即可访问的路径（白名单） */
    private static final String[] WHITE_LIST = {
            // 认证相关
            "/login",
            // 两阶段登录的第二阶段：此时用户还没有令牌，安全性由"一次性票据"保证
            // （票据由服务端签发、绑定账号/设备/网段、用后即焚），因此无需认证即可访问
            "/login/verify",
            "/login/code",
            "/captcha",
            "/refresh",
            // 采集器绑定：此时还没有机器令牌，凭"一次性绑定码"换取，
            // 绑定码 5 分钟有效、一次一用、库里只存摘要，安全性由它保证
            "/kd/binding/redeem",
            // Knife4j / OpenAPI 文档
            "/doc.html",
            "/webjars/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // 监控健康检查
            "/actuator/health",
            "/actuator/info",
            // 静态资源与错误页
            "/favicon.ico",
            "/error"
    };

    /** BCrypt 加密强度（默认 10，可通过配置调整） */
    @Value("${admin.password.bcrypt-strength:10}")
    private int bcryptStrength;

    private final AuthenticationEntryPointImpl authenticationEntryPoint;

    private final AccessDeniedHandlerImpl accessDeniedHandler;

    private final JwtTokenProvider jwtTokenProvider;

    private final TokenBlacklistService tokenBlacklistService;

    private final UserDetailsService userDetailsService;

    /** 采集器令牌校验（桌面端 X-Kd-Token） */
    private final KdTokenService kdTokenService;

    /**
     * 密码加密器：BCrypt（自适应慢哈希，内置随机盐）。
     *
     * <p>严禁使用 MD5 / SHA-1 / 明文存储。
     *
     * @return 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    /**
     * 认证管理器（登录时使用）。
     *
     * @param configuration 认证配置
     * @return 认证管理器
     * @throws Exception 配置获取失败
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * JWT 认证过滤器（以 Bean 方式创建，避免被 Servlet 容器重复注册为全局过滤器）。
     *
     * @return JWT 过滤器
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService, userDetailsService);
    }

    /**
     * 采集器令牌过滤器（Bean 化，避免被 Servlet 容器重复注册）。
     *
     * @return 采集器过滤器
     */
    @Bean
    public KdTokenAuthenticationFilter kdTokenAuthenticationFilter() {
        return new KdTokenAuthenticationFilter(kdTokenService);
    }

    /**
     * 安全过滤链。
     *
     * @param http HttpSecurity
     * @return 过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关闭 CSRF：前后端分离 + JWT，令牌不通过 Cookie 自动携带，不存在 CSRF 场景
                .csrf(csrf -> csrf.disable())
                // 关闭 Spring Security 自带的 LogoutFilter：
                // 它默认接管 "/logout"，会在请求到达我们的 Controller 之前就处理掉并重定向到 /login，
                // 后果是"拉黑当前令牌、清理 refresh 令牌、删除在线会话"这套登出逻辑一次都没执行过 ——
                // 用户看到退出成功，令牌其实还在生效期里。JWT 场景下登出由应用自己实现。
                .logout(AbstractHttpConfigurer::disable)
                // 跨域：复用 CorsConfig 中定义的 CorsConfigurationSource
                .cors(Customizer.withDefaults())
                // 无状态：不创建 HttpSession
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 统一 401 / 403 的 JSON 响应
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST).permitAll()
                        // 除白名单外的所有请求均需认证；细粒度权限由 @PreAuthorize 控制
                        .anyRequest().authenticated())
                // 采集器过滤器先于 JWT：两者都只在自己认识的请求头存在时才建立上下文，
                // 顺序只影响"同时带两种令牌"这种异常请求的判定优先级
                .addFilterBefore(kdTokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                // JWT 过滤器必须在 UsernamePasswordAuthenticationFilter 之前，才能提前建立安全上下文
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                // 安全响应头：逐条说明为什么需要
                // - frameOptions sameOrigin：防点击劫持（此前全局 disable 会让后台界面可被恶意页面透明嵌套，
                //   诱导管理员点"删除用户""分配权限"；sameOrigin 在防劫持与文档页内嵌之间取平衡）
                // - CSP frame-ancestors：frameOptions 的现代替代，object-src 'none' 关闭插件类内容
                // - HSTS：强制 HTTPS，避免首次 HTTP 访问被降级（纯 HTTP 环境下浏览器自动忽略，无副作用）
                // - Referrer-Policy：跨站时不泄露后台 URL 路径
                // - X-Content-Type-Options: nosniff：禁止浏览器嗅探 MIME，防类型混淆执行
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("frame-ancestors 'self'; object-src 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L))
                        .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff")));

        return http.build();
    }
}
