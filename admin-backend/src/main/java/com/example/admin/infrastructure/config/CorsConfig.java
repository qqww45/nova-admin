package com.example.admin.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域（CORS）配置。
 *
 * <p>安全要求：
 * <ul>
 *     <li>允许的来源通过配置项 {@code admin.cors.allowed-origins} 精确列出，禁止使用 {@code *}；</li>
 *     <li>允许的方法与请求头精确列出，不开放无关头；</li>
 *     <li>开启 credentials（前端使用 Bearer Token 时可按需开启 cookie 场景）。</li>
 * </ul>
 *
 * <p>注意：项目同时存在 Spring Security，Security 的 {@code .cors(withDefaults())}
 * 会直接复用此处定义的 {@link CorsConfigurationSource} Bean，无需重复配置。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Configuration
public class CorsConfig {

    /** 允许的来源（逗号分隔，来自配置） */
    @Value("${admin.cors.allowed-origins}")
    private String allowedOrigins;

    /** 允许的方法 */
    @Value("${admin.cors.allowed-methods}")
    private String allowedMethods;

    /** 允许的请求头 */
    @Value("${admin.cors.allowed-headers}")
    private String allowedHeaders;

    /** 是否允许携带凭证 */
    @Value("${admin.cors.allow-credentials}")
    private Boolean allowCredentials;

    /** 预检请求缓存时长 */
    @Value("${admin.cors.max-age}")
    private Long maxAge;

    /**
     * 跨域配置源。
     *
     * @return 基于 URL 的跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        // 禁止通配符，来源为空时直接拒绝跨域，避免误配造成任意站点可访问
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        configuration.setAllowCredentials(Boolean.TRUE.equals(allowCredentials));
        configuration.setMaxAge(maxAge);
        // 暴露给前端读取的响应头（前端需读取自定义头时可在此追加）
        configuration.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        log.info("CORS 已启用，allowedOrigins={}", origins);
        return source;
    }
}
