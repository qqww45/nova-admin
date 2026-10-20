package com.example.admin.infrastructure.security;

import com.example.admin.domain.service.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码加密策略（领域接口 {@link PasswordHasher} 的基础设施实现）。
 *
 * <p>选择 BCrypt 的理由：
 * <ul>
 *     <li>自适应慢哈希，内置随机盐，抗彩虹表；</li>
 *     <li>可通过强度参数（默认 10）随硬件发展提升计算成本；</li>
 *     <li>严禁使用 MD5 / SHA-1 / 明文存储（任务书硬性要求）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class BCryptPasswordHasher implements PasswordHasher {

    /** Spring Security 提供的 PasswordEncoder（由 SecurityConfig 配置为 BCrypt） */
    private final PasswordEncoder passwordEncoder;

    /**
     * 生成密码哈希。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希
     */
    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 校验明文密码与哈希是否匹配。
     *
     * @param rawPassword 明文密码
     * @param hash        已存储的哈希
     * @return 是否匹配
     */
    @Override
    public boolean matches(String rawPassword, String hash) {
        return hash != null && passwordEncoder.matches(rawPassword, hash);
    }
}
