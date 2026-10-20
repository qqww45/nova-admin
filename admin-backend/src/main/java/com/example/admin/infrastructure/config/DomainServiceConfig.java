package com.example.admin.infrastructure.config;

import com.example.admin.domain.repository.MenuRepository;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.service.MenuTreeDomainService;
import com.example.admin.domain.service.PermissionDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域服务的 Bean 注册（Infrastructure 层）。
 *
 * <p>为什么不在领域类上直接加 {@code @Service}：
 * 领域层应当保持"纯 Java"，不引入任何框架注解，
 * 这样领域模型与领域服务才能脱离 Spring 单独做单元测试。
 * 由基础设施层统一装配，体现依赖倒置（领域定义行为，基础设施负责组装）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Configuration
public class DomainServiceConfig {

    /**
     * 注册权限计算领域服务。
     *
     * @param roleRepository 角色仓储
     * @param menuRepository 菜单仓储
     * @return 权限领域服务
     */
    @Bean
    public PermissionDomainService permissionDomainService(RoleRepository roleRepository, MenuRepository menuRepository) {
        return new PermissionDomainService(roleRepository, menuRepository);
    }

    /**
     * 注册菜单树领域服务（无外部依赖，纯算法）。
     *
     * @return 菜单树领域服务
     */
    @Bean
    public MenuTreeDomainService menuTreeDomainService() {
        return new MenuTreeDomainService();
    }
}
