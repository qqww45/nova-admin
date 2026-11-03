package com.example.admin.interfaces.dto.req;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 修改密码请求契约测试：锁定 PUT /user/profile/password 的字段名与校验消息。
 *
 * <p>背景（线上 bug）：前端曾只发送 oldPassword + newPassword，
 * 而本 DTO 的 confirmPassword 带 {@code @NotBlank}——
 * 用户明明填了确认框，却收到「确认密码不能为空」（该错误来自服务端，不是表单校验）。
 * 本测试把契约固化下来：<b>请求体必须包含 confirmPassword</b>，
 * 前端 {@code userApi.changePassword(old, new, confirm)} 与此一一对应。
 */
class ChangePasswordReqTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> violationsOf(ChangePasswordReq req) {
        return validator.validate(req).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private static ChangePasswordReq req(String oldPassword, String newPassword, String confirmPassword) {
        ChangePasswordReq req = new ChangePasswordReq();
        req.setOldPassword(oldPassword);
        req.setNewPassword(newPassword);
        req.setConfirmPassword(confirmPassword);
        return req;
    }

    @Test
    @DisplayName("三字段齐全且合法：无校验违规")
    void validRequest_noViolations() {
        assertThat(violationsOf(req("old-pass-1", "new-pass-1", "new-pass-1"))).isEmpty();
    }

    @Test
    @DisplayName("confirmPassword 缺失：必须报「确认密码不能为空」——前端只发 old+new 的回归点")
    void missingConfirmPassword_violated() {
        Set<String> messages = violationsOf(req("old-pass-1", "new-pass-1", null));
        assertThat(messages).contains("确认密码不能为空");
    }

    @Test
    @DisplayName("三字段全部为空：一次性报出全部必填消息（字段名与消息即前后端契约）")
    void allBlank_allMessagesReported() {
        Set<String> messages = violationsOf(req("", "", ""));
        // 空串的新密码同时触发 @Size(min=5)：长度校验对空值同样生效，属预期行为
        assertThat(messages).containsExactlyInAnyOrder(
                "原密码不能为空",
                "新密码不能为空",
                "确认密码不能为空",
                "新密码长度必须在 5 到 64 之间");
    }

    @Test
    @DisplayName("新密码长度越界（5~64 之外）：报长度校验消息")
    void newPasswordTooShort_violated() {
        Set<String> messages = violationsOf(req("old-pass-1", "1234", "1234"));
        assertThat(messages).anyMatch(m -> m.contains("5 到 64"));
    }
}
