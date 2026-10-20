package com.example.admin.infrastructure.security;

import com.example.admin.common.constant.Constants;
import com.example.admin.domain.model.auth.LoginUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security 适配器：把领域模型 {@link LoginUser} 适配为框架要求的 {@link UserDetails}。
 *
 * <p>为什么要这一层：
 * <ul>
 *     <li>领域模型不应实现框架接口（否则领域层被安全框架污染，难以测试与复用）；</li>
 *     <li>由基础设施层做适配（适配器模式），领域层保持纯粹；</li>
 *     <li>权限 → Authority 的映射规则集中在此，权限判定本身由领域模型 {@code hasPermission} 承担。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class AuthenticatedUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    /** 被适配的领域登录用户 */
    private final LoginUser loginUser;

    /**
     * 构造适配器。
     *
     * @param loginUser 领域登录用户
     */
    public AuthenticatedUser(LoginUser loginUser) {
        this.loginUser = loginUser;
    }

    /**
     * 获取领域登录用户（供 SecurityUtils 与业务代码使用）。
     *
     * @return 登录用户领域模型
     */
    public LoginUser getLoginUser() {
        return this.loginUser;
    }

    /**
     * 获取权限集合：权限标识直接使用，角色标识加 {@code ROLE_} 前缀
     * （兼容 Spring Security 的 {@code hasRole} 表达式习惯）。
     *
     * @return 权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (loginUser.getPermissions() != null) {
            for (String permission : loginUser.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }
        if (loginUser.getRoles() != null) {
            for (String role : loginUser.getRoles()) {
                authorities.add(new SimpleGrantedAuthority(Constants.ROLE_PREFIX + role));
            }
        }
        return authorities;
    }

    /**
     * 获取密码哈希（框架认证流程使用）。
     *
     * @return 密码哈希
     */
    @Override
    public String getPassword() {
        return this.loginUser.getPasswordHash();
    }

    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return this.loginUser.getUsername();
    }

    /**
     * 账号是否可用（停用账号在过滤器中被拒绝）。
     *
     * @return 是否可用
     */
    @Override
    public boolean isEnabled() {
        return this.loginUser.isEnabled();
    }
}
