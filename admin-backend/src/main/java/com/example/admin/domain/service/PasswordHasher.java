package com.example.admin.domain.service;

/**
 * 密码加密策略（领域服务接口）。
 *
 * <p>为什么抽象成接口：BCrypt 是当前选择的算法，但"密码必须单向哈希"是领域规则，
 * 算法是技术细节。若未来迁移到 Argon2，只需替换 Infrastructure 层的实现，
 * 领域模型 {@code User} 完全不用改（策略模式 + 依赖倒置）。
 *
 * <p>禁止事项：实现中不得使用 MD5 / SHA-1 / 明文存储。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface PasswordHasher {

    /**
     * 生成密码哈希。
     *
     * @param rawPassword 明文密码
     * @return 哈希值（BCrypt 自带随机盐，无需额外存储盐值）
     */
    String encode(String rawPassword);

    /**
     * 校验明文密码与哈希是否匹配。
     *
     * @param rawPassword 明文密码
     * @param hash        已存储的哈希
     * @return 是否匹配
     */
    boolean matches(String rawPassword, String hash);
}
