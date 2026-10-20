package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.port.LoginUserCache;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.service.PermissionDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户加载服务（Spring Security {@link UserDetailsService} 实现）。
 *
 * <p>职责：按用户名加载用户聚合 → 通过权限领域服务推导角色与权限 → 适配为框架模型。
 * 本类不含业务规则，仅做"领域模型 → 框架模型"的装配。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    private final PermissionDomainService permissionDomainService;

    private final LoginUserCache loginUserCache;

    /**
     * 按用户名加载用户。
     *
     * <p>安全说明：用户不存在时不区分具体原因（由上层统一提示"用户名或密码错误"），
     * 避免账号枚举攻击。
     *
     * @param username 用户名
     * @return 框架用户模型（适配器）
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 命中缓存即可跳过"用户 → 角色 → 菜单权限"整条查询链：
        // 这条链每个请求都要走一遍，而它的数据几乎不变。
        LoginUser cached = loginUserCache.get(username);
        if (cached != null) {
            return new AuthenticatedUser(cached);
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        LoginUser loginUser = permissionDomainService.buildLoginUser(user);
        loginUserCache.put(username, loginUser);
        // 返回副本：tokenId / deviceId 是每请求各不相同的，不能让它写进缓存里的对象
        return new AuthenticatedUser(loginUser.copyForRequest());
    }
}
