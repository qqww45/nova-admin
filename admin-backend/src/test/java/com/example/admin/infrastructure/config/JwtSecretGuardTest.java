package com.example.admin.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 密钥守卫测试。
 *
 * <p>为什么要为它写测试：这个守卫只在 prod profile 生效，本地开发永远不会跑到，
 * 一旦失效（比如有人改动默认值）要到生产部署时才发现 —— 而那时它要防的事已经发生了。
 *
 * @author admin-system
 * @since 2026-01-01
 */
class JwtSecretGuardTest {

    private static final String DEFAULT_SECRET =
            "admin-system-default-jwt-secret-please-change-in-production-32bytes";

    private static JwtSecretGuard guardWith(String secret) {
        JwtSecretGuard guard = new JwtSecretGuard();
        ReflectionTestUtils.setField(guard, "secret", secret);
        return guard;
    }

    @Test
    @DisplayName("未注入密钥：拒绝启动")
    void check_blankSecret_rejected() {
        assertThatThrownBy(() -> guardWith("").check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("仓库里的默认密钥：即使长度合规也必须拒绝")
    void check_defaultSecret_rejected() {
        // 这是本守卫存在的核心理由：默认串长度 > 32，能通过长度校验，但内容是公开的
        assertThatThrownBy(() -> guardWith(DEFAULT_SECRET).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认");
    }

    @Test
    @DisplayName("常见弱密钥：拒绝")
    void check_weakSecret_rejected() {
        assertThatThrownBy(() -> guardWith("changeit").check())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("长度不足 32 字节：拒绝")
    void check_tooShort_rejected() {
        assertThatThrownBy(() -> guardWith("short-secret").check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("长度不足");
    }

    @Test
    @DisplayName("低熵密钥（重复字符）：拒绝")
    void check_lowEntropy_rejected() {
        assertThatThrownBy(() -> guardWith("a".repeat(40)).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("熵过低");
    }

    @Test
    @DisplayName("合规的强随机密钥：通过")
    void check_strongSecret_passed() {
        String strong = "9f2b7c1e-4a83-4d16-9e5f-8c2a7b3d6e1f-A8f3C9d2E7b1";
        assertThatCode(() -> guardWith(strong).check()).doesNotThrowAnyException();
    }
}
