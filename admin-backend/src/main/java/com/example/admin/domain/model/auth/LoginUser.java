package com.example.admin.domain.model.auth;

import com.example.admin.common.constant.Constants;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 登录用户（认证上下文领域模型）。
 *
 * <p>与 Spring Security 解耦：本类不实现 {@code UserDetails}，
 * 由 Infrastructure 层的 {@code AuthenticatedUser} 做适配器，
 * 保证领域模型不被安全框架污染（依赖倒置）。
 *
 * <p>权限判定策略：
 * <ul>
 *     <li>超级管理员角色注入通配符 {@code *:*:*}，天然拥有全部权限；</li>
 *     <li>其余按精确匹配 permissions 集合。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class LoginUser {

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 密码哈希（认证通过后框架需要，但不会出现在任何响应与日志中） */
    private String passwordHash;

    /** 昵称 */
    private String nickname;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 部门 ID */
    private Long deptId;

    /** 主题偏好 */
    private String theme;

    /** 当前访问令牌 ID（jti，用于登出拉黑） */
    private String tokenId;

    /**
     * 当前登录所属的设备标识。
     *
     * <p>用途：登出时前端若不传 refreshToken（该参数是可选的），
     * 服务端仍要能按设备清理刷新令牌与在线会话，否则"已退出登录"只是假象。
     */
    private String deviceId;

    /** 角色标识集合 */
    private Set<String> roles = new HashSet<>();

    /** 权限标识集合 */
    private Set<String> permissions = new HashSet<>();

    /**
     * 判断是否拥有指定权限。
     *
     * @param permission 权限标识
     * @return 是否拥有
     */
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        if (this.permissions.contains(Constants.ALL_PERMISSION)) {
            return true;
        }
        for (String owned : this.permissions) {
            // 支持 xxx:* 形式的通配（菜单权限可按模块授权）
            if (owned.equals(permission) || wildcardMatch(owned, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否拥有指定角色。
     *
     * @param roleKey 角色标识
     * @return 是否拥有
     */
    public boolean hasRole(String roleKey) {
        return this.roles.contains(roleKey);
    }

    /**
     * 判断是否为超级管理员。
     *
     * @return 是否超级管理员
     */
    public boolean isSuperAdmin() {
        return Constants.SUPER_ADMIN_USER_ID.equals(this.userId) || this.roles.contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }

    /**
     * 判断账号是否可用（停用账号在刷新令牌时会被拒绝）。
     *
     * @return 是否可用
     */
    public boolean isEnabled() {
        return Constants.STATUS_NORMAL.equals(this.status);
    }

    /**
     * 获取权限标识集合（不可变视图）。
     *
     * @return 权限集合
     */
    public Set<String> getPermissions() {
        return this.permissions == null ? Collections.emptySet() : Collections.unmodifiableSet(this.permissions);
    }

    /**
     * 复制一份"与请求无关"的副本（用于缓存）。
     *
     * <p>为什么要复制而不是直接缓存 {@code this}：{@code tokenId} 与 {@code deviceId}
     * 是每请求各不相同的（哪个令牌、哪台设备），它们会被认证过滤器写进同一个对象。
     * 如果缓存了原对象，并发请求就会互相覆盖这两个字段 ——
     * 登出时按 tokenId 拉黑、按 deviceId 清理设备，都会作用到"别人的"令牌和设备上。
     *
     * @return 不含 tokenId / deviceId 的副本
     */
    public LoginUser copyForRequest() {
        LoginUser copy = new LoginUser();
        copy.userId = this.userId;
        copy.username = this.username;
        copy.passwordHash = this.passwordHash;
        copy.nickname = this.nickname;
        copy.status = this.status;
        copy.deptId = this.deptId;
        copy.theme = this.theme;
        copy.roles = this.roles == null ? new HashSet<>() : new HashSet<>(this.roles);
        copy.permissions = this.permissions == null ? new HashSet<>() : new HashSet<>(this.permissions);
        return copy;
    }

    /**
     * 通配匹配：owned 形如 system:user:* 时匹配 system:user:add。
     *
     * @param owned      已拥有的权限表达式
     * @param permission 待校验权限
     * @return 是否匹配
     */
    private boolean wildcardMatch(String owned, String permission) {
        if (!owned.endsWith("*")) {
            return false;
        }
        String prefix = owned.substring(0, owned.length() - 1);
        return !prefix.isEmpty() && permission.startsWith(prefix);
    }
}
