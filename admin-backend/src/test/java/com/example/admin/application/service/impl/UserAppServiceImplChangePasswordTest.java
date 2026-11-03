package com.example.admin.application.service.impl;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.port.LoginUserCache;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.service.DataScopePolicy;
import com.example.admin.domain.service.PasswordHasher;
import com.example.admin.application.assembler.UserAssembler;
import com.example.admin.interfaces.dto.req.ChangePasswordReq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 修改密码（个人中心）应用服务测试。
 *
 * <p>契约：confirmPassword 与 newPassword 不一致时必须拒绝（PARAM_ERROR），
 * 即使两者都已通过 DTO 的 @NotBlank 校验——两次一致性是应用层兜底，
 * 前端确认框只是第一道闸门。
 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceImplChangePasswordTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private UserAssembler userAssembler;

    @Mock
    private OperatorContext operatorContext;

    @Mock
    private LoginUserCache loginUserCache;

    @Mock
    private DataScopePolicy dataScopePolicy;

    @InjectMocks
    private UserAppServiceImpl service;

    private static ChangePasswordReq req(String oldPwd, String newPwd, String confirmPwd) {
        ChangePasswordReq req = new ChangePasswordReq();
        req.setOldPassword(oldPwd);
        req.setNewPassword(newPwd);
        req.setConfirmPassword(confirmPwd);
        return req;
    }

    private User userWithPasswordHash() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("hash");
        return user;
    }

    @Test
    @DisplayName("confirmPassword 与 newPassword 不一致：拒绝（两次输入的新密码不一致）")
    void changePassword_confirmMismatch_throws() {
        ChangePasswordReq req = req("old-pass", "new-pass-1", "new-pass-2");

        assertThatThrownBy(() -> service.changePassword(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("两次输入的新密码不一致");
        // 不一致时必须先于任何用户加载/落库被拦截
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("原密码正确、两次一致：改密成功并落库")
    void changePassword_valid_saves() {
        ChangePasswordReq req = req("old-pass", "new-pass", "new-pass");
        User user = userWithPasswordHash();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordHasher.matches("old-pass", "hash")).thenReturn(true);
        when(passwordHasher.matches("new-pass", "hash")).thenReturn(false);
        when(passwordHasher.encode("new-pass")).thenReturn("hash-new");
        lenient().when(userRepository.save(any())).thenReturn(user);

        assertThatCode(() -> service.changePassword(1L, req)).doesNotThrowAnyException();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("原密码错误：报「原密码错误」，不改库")
    void changePassword_wrongOldPassword_throws() {
        ChangePasswordReq req = req("wrong-old", "new-pass", "new-pass");
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPasswordHash()));
        when(passwordHasher.matches(eq("wrong-old"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原密码错误");
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("新密码与原密码相同：拒绝（新密码不能与原密码相同）")
    void changePassword_sameAsOld_throws() {
        ChangePasswordReq req = req("same-pass", "same-pass", "same-pass");
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPasswordHash()));
        // 聚合规则：旧密码匹配 + 新密码也匹配旧哈希 → SAME_PASSWORD
        when(passwordHasher.matches("same-pass", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能与原密码相同");
    }

    @Test
    @DisplayName("用户不存在：报数据不存在")
    void changePassword_userMissing_throws() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(9L, req("old", "new-pass", "new-pass")))
                .isInstanceOf(BusinessException.class);
    }
}
