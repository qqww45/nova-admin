package com.example.admin.infrastructure.util;

import com.example.admin.common.constant.Constants;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.infrastructure.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * 安全工具类：便捷获取当前登录用户上下文。
 *
 * <p>典型用法：
 * <pre>{@code
 *     Long userId = SecurityUtils.getUserId();
 *     if (SecurityUtils.hasPermission("system:user:add")) { ... }
 * }</pre>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityUtils {

    /**
     * 获取当前认证对象。
     *
     * @return 认证对象，未登录返回 null
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 获取当前登录用户。
     *
     * @return 登录用户上下文，未登录或主体非 {@link LoginUser} 时返回 null
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        // 主体是框架适配器 AuthenticatedUser，真正的领域模型由它持有（防腐层）
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.getLoginUser();
        }
        return null;
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     * @throws IllegalStateException 当请求上下文无登录用户时抛出（正常已被安全过滤器拦截）
     */
    public static Long getUserId() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            throw new IllegalStateException("当前请求未绑定登录用户");
        }
        return loginUser.getUserId();
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null（用于自动填充等“可空”场景）。
     *
     * @return 用户 ID 或 null
     */
    public static Long getUserIdOrNull() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 用户名，未登录返回 null
     */
    public static String getUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUsername();
    }

    /**
     * 判断当前用户是否为超级管理员。
     *
     * @return 是否超级管理员
     */
    public static boolean isAdmin() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || loginUser.getRoles() == null) {
            return false;
        }
        return loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }

    /**
     * 判断当前用户是否拥有指定权限（含超级管理员通配）。
     *
     * @param permission 权限标识，如 system:user:add
     * @return 是否拥有
     */
    public static boolean hasPermission(String permission) {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            return false;
        }
        Set<String> permissions = loginUser.getPermissions();
        if (permissions == null) {
            return false;
        }
        return permissions.contains(Constants.ALL_PERMISSION) || permissions.contains(permission);
    }
}
