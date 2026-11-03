package com.example.admin.infrastructure.config;

import com.example.admin.domain.model.user.User;
import com.example.admin.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 初始口令守卫单元测试：内置账号仍在用脚本默认口令时，生产 profile 必须拒绝启动。
 *
 * <p>背景：迁移脚本落了 {@code admin/admin123} 等内置账号，口令明文随仓库分发；
 * 部署后忘记改密，等于把超管权限（{@code *:*:*}）公开。守卫把
 * "忘记改密"从静默沦陷变成启动失败 + 明确原因。
 */
@ExtendWith(MockitoExtension.class)
class DefaultCredentialGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DefaultCredentialGuard guard;

    private User seedUser(long id, String username, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        return user;
    }

    @Test
    @DisplayName("admin 仍用初始口令 admin123：拒绝启动并点名账号")
    void check_adminWithDefaultPassword_throws() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(seedUser(1L, "admin", "hash-admin")));
        when(passwordEncoder.matches(eq("admin123"), anyString())).thenReturn(true);

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin")
                .hasMessageContaining("初始口令");
    }

    @Test
    @DisplayName("任一内置账号命中默认口令（manager/test 口令）都拒绝启动")
    void check_anySeedWithDefaultPassword_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        when(userRepository.findById(3L)).thenReturn(Optional.of(seedUser(3L, "test", "hash-test")));
        // 守卫会按 admin123 -> test123 -> 123456 的顺序逐一比对：
        // 前两次必须显式返回 false，命中 123456 才能覆盖"列表中后面的默认口令"分支
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.matches("123456", "hash-test")).thenReturn(true);

        assertThatThrownBy(guard::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test");
    }

    @Test
    @DisplayName("全部内置账号已改密：放行")
    void check_allPasswordsChanged_passes() {
        lenient().when(userRepository.findById(1L))
                .thenReturn(Optional.of(seedUser(1L, "admin", "hash-admin")));
        lenient().when(userRepository.findById(2L))
                .thenReturn(Optional.of(seedUser(2L, "manager", "hash-manager")));
        lenient().when(userRepository.findById(3L))
                .thenReturn(Optional.of(seedUser(3L, "test", "hash-test")));
        // 三种默认口令逐一比对都失败：说明口令已全部更换
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatCode(guard::check).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("内置账号不存在（如已手工清理）：跳过该账号不报错")
    void check_seedUserMissing_passes() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatCode(guard::check).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("passwordHash 为 null 的账号（如 LDAP 之类无口令账号）：跳过")
    void check_nullHashSkipped() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(seedUser(1L, "admin", null)));

        assertThatCode(guard::check).doesNotThrowAnyException();
    }
}
