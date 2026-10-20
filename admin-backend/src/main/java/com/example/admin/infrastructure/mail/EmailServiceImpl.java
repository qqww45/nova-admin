package com.example.admin.infrastructure.mail;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 邮件服务实现（Spring {@code JavaMailSender} + SMTP）。
 *
 * <p>配置位置（{@code application.yml} 或环境变量）：
 * <pre>
 * spring.mail.host=${MAIL_HOST}
 * spring.mail.port=${MAIL_PORT:465}
 * spring.mail.username=${MAIL_USERNAME}
 * spring.mail.password=${MAIL_PASSWORD}   # 多数邮件商使用"授权码"，不是登录密码
 * admin.mail.from=${MAIL_FROM:}           # 留空时取 spring.mail.username
 * </pre>
 *
 * <p>实现要点：
 * <ul>
 *     <li>统一用 {@link MimeMessageHelper}（而非 {@code SimpleMailMessage}）：
 *         支持 HTML 正文与 UTF-8 编码，中文主题/正文不会乱码；</li>
 *     <li>发件人缺省取 {@code spring.mail.username}：多数 SMTP 要求发件人与登录账号一致；</li>
 *     <li>失败一律转换为业务异常：调用方无需处理 {@code MailException} 这类基础设施异常。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    /** 默认发件人：留空时回落 spring.mail.username */
    @Value("${admin.mail.from:${spring.mail.username:}}")
    private String defaultFrom;

    /** SMTP 主机：用于判断服务是否已配置 */
    @Value("${spring.mail.host:}")
    private String host;

    @Override
    public void send(EmailMessage message) {
        if (message == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邮件内容不能为空");
        }
        if (!isConfigured()) {
            throw new BusinessException(ResultCode.ONE_TIME_CODE_SEND_FAILED,
                    "邮件服务未配置（spring.mail.host 为空），无法发送邮件");
        }
        String to = message.getTo();
        if (to == null || to.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收件人不能为空");
        }
        String from = (defaultFrom == null || defaultFrom.isBlank()) ? null : defaultFrom;
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            // multipart=false：当前不支持附件；需要时改为 true 并加 addAttachment 即可
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            if (from != null) {
                helper.setFrom(from);
            }
            helper.setTo(to.split(","));
            List<String> cc = message.getCc();
            if (cc != null && !cc.isEmpty()) {
                helper.setCc(cc.toArray(new String[0]));
            }
            List<String> bcc = message.getBcc();
            if (bcc != null && !bcc.isEmpty()) {
                helper.setBcc(bcc.toArray(new String[0]));
            }
            helper.setSubject(message.getSubject() == null ? "" : message.getSubject());
            helper.setText(message.getText() == null ? "" : message.getText(), message.isHtml());
            mailSender.send(mime);
            log.debug("邮件已发送至 {}", to);
        } catch (MailException | jakarta.mail.MessagingException ex) {
            log.error("邮件发送失败 to={} subject={} cause={}", to, message.getSubject(), ex.getMessage());
            throw new BusinessException(ResultCode.ONE_TIME_CODE_SEND_FAILED, "邮件发送失败，请稍后重试或联系管理员");
        }
    }

    @Override
    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }
}
