package com.example.admin.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI3 接口文档配置。
 *
 * <p>访问入口：{@code http://localhost:8080/doc.html}（Knife4j 4.x 原生支持暗色主题切换）。
 * 文档会自动读取各 Controller 上的 {@code @Operation}、{@code @Schema} 注解。
 *
 * <p>此处统一声明 Bearer 认证，使文档调试时可一键携带 JWT，无需手工拼接 Header。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名称，与 {@link SecurityRequirement} 保持一致 */
    private static final String SECURITY_SCHEME_NAME = "Bearer";

    /**
     * 构建 OpenAPI 描述信息。
     *
     * @return OpenAPI 对象
     */
    @Bean
    public OpenAPI adminOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("通用后端管理系统 API 文档")
                        .description("Spring Boot 3.3 + MyBatis-Plus + Spring Security + JWT 的通用后台管理接口")
                        .version("v1.0.0")
                        .contact(new Contact().name("admin-system"))
                        .license(new License().name("Apache License 2.0")))
                // 声明 JWT 认证方式
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("请在下方输入登录接口返回的 Access Token（无需加 Bearer 前缀）")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
