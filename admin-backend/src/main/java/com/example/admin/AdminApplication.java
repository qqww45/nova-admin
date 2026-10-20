package com.example.admin;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 通用后端管理系统 - 启动类。
 *
 * <p>职责：
 * <ul>
 *     <li>标注 {@link SpringBootApplication} 开启自动装配与组件扫描；</li>
 *     <li>通过 {@link MapperScan} 扫描 MyBatis-Plus Mapper 接口；</li>
 *     <li>通过 {@link EnableMethodSecurity} 开启 {@code @PreAuthorize} 方法级鉴权（RBAC 接口权限的核心）；</li>
 *     <li>通过 {@link EnableScheduling} 开启定时任务（用于令牌黑名单过期清理等）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.example.admin.infrastructure.persistence.mapper")
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableScheduling
public class AdminApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数（可通过 --spring.profiles.active=prod 切换环境）
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
        // 注意：此处禁止打印任何敏感信息（密钥、密码、令牌）
        log.info("Admin backend started successfully.");
    }
}
