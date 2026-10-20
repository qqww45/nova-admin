package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.infrastructure.util.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * {@link OperatorContext} 的 Spring Security 实现。
 *
 * <p>把"从 SecurityContextHolder 里取当前登录人"这件基础设施的事留在基础设施层，
 * 应用层只依赖 {@link OperatorContext} 接口。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class SecurityOperatorContext implements OperatorContext {

    @Override
    public LoginUser current() {
        return SecurityUtils.getLoginUser();
    }

    @Override
    public Long currentUserIdOrNull() {
        return SecurityUtils.getUserIdOrNull();
    }

    @Override
    public Long currentUserId() {
        return SecurityUtils.getUserId();
    }
}
