package com.example.admin.infrastructure.security;

import cn.hutool.core.util.StrUtil;
import com.example.admin.common.constant.Constants;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.infrastructure.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;

/**
 * 权限校验服务（Spring Security 表达式中的 {@code @ss} Bean）。
 *
 * <p>用法（Controller 方法级鉴权）：
 * <pre>{@code
 *     @PreAuthorize("@ss.hasPermi('system:user:add')")
 *     public R<Void> add(...) { ... }
 *
 *     @PreAuthorize("@ss.hasAnyPermi('system:user:add,system:user:edit')")
 *     public R<Void> saveOrUpdate(...) { ... }
 * }</pre>
 *
 * <p>为什么需要它：Spring Security 的 {@code hasAuthority} 只能判断固定字符串，
 * 而我们希望权限判断统一走“当前登录用户的权限集合 + 超级管理员通配”逻辑，
 * 抽成 Bean 后表达式更简洁，也便于后续接入数据权限。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service("ss")
public class PermissionService {

    /**
     * 判断当前用户是否拥有指定权限。
     *
     * @param permission 权限标识，如 system:user:add
     * @return 是否拥有
     */
    public boolean hasPermi(String permission) {
        if (StrUtil.isBlank(permission)) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            return false;
        }
        return hasAnyPermission(loginUser.getPermissions(), permission);
    }

    /**
     * 判断当前用户是否拥有其中任意一个权限（多个用逗号分隔）。
     *
     * @param permissions 权限标识，多个用英文逗号分隔
     * @return 是否拥有任意一个
     */
    public boolean hasAnyPermi(String permissions) {
        if (StrUtil.isBlank(permissions)) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            return false;
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .anyMatch(permission -> hasAnyPermission(loginUser.getPermissions(), permission));
    }

    /**
     * 判断当前用户是否拥有指定角色。
     *
     * @param roleKey 角色标识，如 admin
     * @return 是否拥有
     */
    public boolean hasRole(String roleKey) {
        if (StrUtil.isBlank(roleKey)) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getRoles() == null) {
            return false;
        }
        return loginUser.getRoles().contains(roleKey);
    }

    /**
     * 判断当前用户是否拥有其中任意一个角色（多个用逗号分隔）。
     *
     * @param roleKeys 角色标识，多个用英文逗号分隔
     * @return 是否拥有任意一个
     */
    public boolean hasAnyRole(String roleKeys) {
        if (StrUtil.isBlank(roleKeys)) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getRoles() == null) {
            return false;
        }
        return Arrays.stream(roleKeys.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .anyMatch(loginUser.getRoles()::contains);
    }

    /**
     * 内部工具：判断权限集合是否命中（超级管理员通配符 {@code *:*:*} 直接放行）。
     *
     * @param permissions 当前用户权限集合
     * @param permission  待校验权限
     * @return 是否命中
     */
    private boolean hasAnyPermission(Set<String> permissions, String permission) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.contains(Constants.ALL_PERMISSION) || permissions.contains(permission);
    }
}
