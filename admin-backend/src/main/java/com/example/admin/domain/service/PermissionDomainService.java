package com.example.admin.domain.service;

import com.example.admin.common.constant.Constants;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.menu.Menu;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.repository.MenuRepository;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.model.user.User;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限计算领域服务。
 *
 * <p>职责：把"用户 → 角色 → 菜单 → 权限标识"的推导过程收敛到领域层，
 * 供认证（加载登录用户）与应用服务复用，避免同一套规则在两处实现（会出现不一致）。
 *
 * <p>规则：
 * <ol>
 *     <li>仅启用状态的角色参与鉴权；</li>
 *     <li>仅启用状态的菜单提供权限标识，空 permission 跳过；</li>
 *     <li>超级管理员注入通配符 {@code *:*:*}，天然拥有全部权限。</li>
 * </ol>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class PermissionDomainService {

    private final RoleRepository roleRepository;

    private final MenuRepository menuRepository;

    /**
     * 构造领域服务（依赖仓储接口，由 Spring 注入具体实现）。
     *
     * @param roleRepository 角色仓储
     * @param menuRepository 菜单仓储
     */
    public PermissionDomainService(RoleRepository roleRepository, MenuRepository menuRepository) {
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
    }

    /**
     * 根据用户拥有的角色 ID 计算权限标识集合。
     *
     * @param roleIds 用户角色 ID 集合
     * @return 权限标识集合
     */
    public Set<String> permissionsOf(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        return permissionsOf(roleRepository.findByIds(roleIds));
    }

    /**
     * 由已加载的角色集合计算权限（避免同一请求内重复查库）。
     *
     * @param roles 角色集合
     * @return 权限标识集合
     */
    private Set<String> permissionsOf(List<Role> roles) {
        List<Long> effectiveRoleIds = roles.stream()
                .filter(Role::isEnabled)
                .map(Role::getId)
                .collect(Collectors.toList());
        if (effectiveRoleIds.isEmpty()) {
            return new HashSet<>();
        }
        return menuRepository.findByRoleIds(effectiveRoleIds).stream()
                .filter(menu -> Constants.STATUS_NORMAL.equals(menu.getStatus()))
                .map(Menu::getPermission)
                .filter(permission -> permission != null && !permission.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * 计算用户的角色标识集合（仅启用角色）。
     *
     * @param roleIds 用户角色 ID 集合
     * @return 角色标识集合
     */
    public Set<String> roleKeysOf(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        return roleKeysOf(roleRepository.findByIds(roleIds));
    }

    /**
     * 由已加载的角色集合提取角色标识（避免同一请求内重复查库）。
     *
     * @param roles 角色集合
     * @return 角色标识集合
     */
    private Set<String> roleKeysOf(List<Role> roles) {
        return roles.stream()
                .filter(Role::isEnabled)
                .map(Role::getRoleKey)
                .collect(Collectors.toSet());
    }

    /**
     * 由用户聚合构建登录用户（认证上下文）。
     *
     * <p>这是"用户 → 角色 → 权限"推导的唯一入口，认证加载（JWT 过滤器）与登录签发令牌
     * 都调用它，保证两处权限计算结果一致（避免"登录时能访问、刷新后不能访问"的诡异问题）。
     *
     * @param user 用户聚合根
     * @return 登录用户（含角色与权限）
     */
    public LoginUser buildLoginUser(User user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPasswordHash(user.getPasswordHash());
        loginUser.setNickname(user.getNickname());
        loginUser.setStatus(user.getStatus());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setTheme(user.getTheme());
        // 角色只查一次：roleKeysOf 与 permissionsOf 各查一遍意味着每个请求白扔一次 selectBatchIds。
        // 这里是"用户 → 角色 → 权限"的必经之路，认证过滤器每请求都会走一次。
        List<Role> roles = user.getRoleIds() == null || user.getRoleIds().isEmpty()
                ? List.of()
                : roleRepository.findByIds(user.getRoleIds());
        loginUser.setRoles(roleKeysOf(roles));
        loginUser.setPermissions(permissionsOf(roles));
        applySuperAdminPrivileges(loginUser);
        return loginUser;
    }

    /**
     * 补齐超级管理员的通配权限（在权限集合上原地追加）。
     *
     * @param loginUser 登录用户
     */
    public void applySuperAdminPrivileges(LoginUser loginUser) {
        if (loginUser.isSuperAdmin()) {
            // getPermissions() 返回不可变视图，因此先复制一份再追加通配符权限
            Set<String> all = new HashSet<>(loginUser.getPermissions());
            all.add(Constants.ALL_PERMISSION);
            loginUser.setPermissions(all);
        }
    }
}
