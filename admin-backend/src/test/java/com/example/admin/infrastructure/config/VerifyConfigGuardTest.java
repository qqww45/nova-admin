package com.example.admin.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生产配置守卫单元测试：验证"配错就起不来"的各项触发条件。
 *
 * <p>字段由 {@code @Value} 注入，测试中用反射直接赋值以覆盖各种组合。
 */
class VerifyConfigGuardTest {

    private VerifyConfigGuard guard(boolean revealCode, String channel,
                                    String host, String username, String password) {
        VerifyConfigGuard guard = new VerifyConfigGuard();
        ReflectionTestUtils.setField(guard, "revealCode", revealCode);
        ReflectionTestUtils.setField(guard, "channel", channel);
        ReflectionTestUtils.setField(guard, "mailHost", host);
        ReflectionTestUtils.setField(guard, "mailUsername", username);
        ReflectionTestUtils.setField(guard, "mailPassword", password);
        return guard;
    }

    @Test
    @DisplayName("生产正确配置：channel=email、不回传验证码、邮件三项齐全 → 放行")
    void check_validConfig_passes() {
        VerifyConfigGuard guard = guard(false, "email", "smtp.example.com", "noreply@x.com", "secret");

        assertThatCode(guard::check).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("开启 reveal-code：拒绝启动（验证码会随响应返回）")
    void check_revealCodeEnabled_throws() {
        VerifyConfigGuard guard = guard(true, "email", "smtp.example.com", "noreply@x.com", "secret");

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reveal-code");
    }

    @Test
    @DisplayName("生产仍用 log 通道：拒绝启动（验证码不会送达用户）")
    void check_logChannel_throws() {
        VerifyConfigGuard guard = guard(false, "log", "smtp.example.com", "noreply@x.com", "secret");

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("channel");
    }

    @Test
    @DisplayName("邮件 host 未配置：拒绝启动")
    void check_mailHostMissing_throws() {
        VerifyConfigGuard guard = guard(false, "email", "", "noreply@x.com", "secret");

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }

    @Test
    @DisplayName("邮件账号未配置：拒绝启动")
    void check_mailUsernameMissing_throws() {
        VerifyConfigGuard guard = guard(false, "email", "smtp.example.com", "", "secret");

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.username");
    }

    @Test
    @DisplayName("邮件密码/授权码未配置：拒绝启动")
    void check_mailPasswordMissing_throws() {
        VerifyConfigGuard guard = guard(false, "email", "smtp.example.com", "noreply@x.com", "");

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.password");
    }

    @Test
    @DisplayName("多项违规：一次性列出全部问题（便于一次修完，不用反复重启）")
    void check_multipleProblems_reportsAll() {
        VerifyConfigGuard guard = guard(true, "log", "", "", "");

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reveal-code")
                .hasMessageContaining("channel")
                .hasMessageContaining("spring.mail.host")
                .hasMessageContaining("spring.mail.username")
                .hasMessageContaining("spring.mail.password");
    }

    @Test
    @DisplayName("channel 大小写不敏感：EMAIL 视为 email")
    void check_channelCaseInsensitive() {
        VerifyConfigGuard guard = guard(false, "EMAIL", "smtp.example.com", "noreply@x.com", "secret");

        assertThatCode(guard::check).doesNotThrowAnyException();
    }
}
