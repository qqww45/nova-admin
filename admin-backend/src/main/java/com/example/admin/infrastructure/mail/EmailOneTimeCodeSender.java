package com.example.admin.infrastructure.mail;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.domain.model.auth.OneTimeCodeMessage;
import com.example.admin.domain.service.OneTimeCodeSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 一次性码邮件发送实现（生产通道）。
 *
 * <p>启用条件：{@code admin.verify.channel=email} 且正确配置 {@code spring.mail.*}。
 *
 * <p>本类是"通用邮件能力"（{@link EmailService}）的一个业务用例：
 * 只负责把验证码组织成邮件内容，发送细节（SMTP、编码、失败处理）全部交给 {@link EmailService}。
 * 未来要做"登录提醒邮件""异常登录告警"等，直接复用同一个 {@link EmailService} 即可。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.verify.channel", havingValue = "email")
public class EmailOneTimeCodeSender implements OneTimeCodeSender {

    private final EmailService emailService;

    /** 邮件主题前缀（便于用户识别来源） */
    @Value("${admin.verify.mail.subject-prefix:[Admin]}")
    private String subjectPrefix;

    /** 验证码有效期（秒），用于在邮件正文提示用户 */
    @Value("${admin.verify.one-time-code-ttl-seconds:300}")
    private long ttlSeconds;

    @Override
    public void send(OneTimeCodeMessage message) {
        String email = message.getEmail();
        if (email == null || email.isBlank() || !email.contains("@")) {
            // 用户没绑邮箱：直接抛错，由管理员/运营补全而不是静默用日志冒充"已发送"
            throw new BusinessException(ResultCode.ONE_TIME_CODE_TARGET_MISSING, "该账号未绑定邮箱，无法下发验证码");
        }
        EmailMessage mail = EmailMessage.builder()
                .to(email)
                .subject(subjectPrefix + " 登录验证码")
                .text(buildBody(message.getCode()))
                .html(false)
                .build();
        try {
            emailService.send(mail);
            // 日志不打印验证码本身（生产）：审计与排障看网关日志即可
            log.info("已向用户 {} 发送登录验证码", message.getUsername());
        } catch (BusinessException ex) {
            // 日志脱敏：邮箱属于个人信息，排障看用户名即可，不必落全量邮箱
            log.error("登录验证码邮件发送失败 username={} email={} cause={}",
                    message.getUsername(), maskEmail(email), ex.getMessage());
            throw ex;
        }
    }

    /**
     * 邮箱脱敏：日志与排障信息里不落全量邮箱（个人信息最小化）。
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱
     */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String name = email.substring(0, at);
        return (name.length() <= 1 ? name : name.charAt(0) + "***") + email.substring(at);
    }

    /**
     * 构造纯文本正文（不用于 HTML）：验证码邮件以"可读性 + 可复制"优先。
     */
    private String buildBody(String code) {
        long minutes = Math.max(1, ttlSeconds / 60);
        return "您正在登录后台管理系统。\n" +
                "本次验证码：" + code + "（" + minutes + " 分钟内有效，请勿泄露给他人）。\n" +
                "如非本人操作，请忽略本邮件。";
    }
}
