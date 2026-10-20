package com.example.admin.infrastructure.mail;

import com.example.admin.common.exception.BusinessException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件服务单元测试：正常发送、未配置、参数校验与发送失败。
 */
class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service = new EmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(service, "defaultFrom", "noreply@example.com");
        ReflectionTestUtils.setField(service, "host", "smtp.example.com");
    }

    private static EmailMessage message(String to, String subject, String text) {
        return EmailMessage.builder().to(to).subject(subject).text(text).html(false).build();
    }

    // ---------------- 正常流程 ----------------

    @Test
    @DisplayName("已配置且参数合法：发送成功")
    void send_valid_sends() {
        assertThatCode(() -> service.send(message("user@example.com", "主题", "正文")))
                .doesNotThrowAnyException();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("支持 HTML 正文与抄送/密送")
    void send_withHtmlAndCc_sends() {
        EmailMessage msg = EmailMessage.builder()
                .to("a@example.com,b@example.com")
                .cc(List.of("cc@example.com"))
                .bcc(List.of("bcc@example.com"))
                .subject("HTML 邮件")
                .text("<h1>标题</h1>")
                .html(true)
                .build();

        assertThatCode(() -> service.send(msg)).doesNotThrowAnyException();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("isConfigured：host 非空为 true，空为 false")
    void isConfigured_various() {
        assertThat(service.isConfigured()).isTrue();

        ReflectionTestUtils.setField(service, "host", "");
        assertThat(service.isConfigured()).isFalse();

        ReflectionTestUtils.setField(service, "host", null);
        assertThat(service.isConfigured()).isFalse();
    }

    // ---------------- 异常场景 ----------------

    @Test
    @DisplayName("未配置 SMTP：抛业务异常（不允许'假装发出去了'）")
    void send_notConfigured_throws() {
        ReflectionTestUtils.setField(service, "host", "");

        assertThatThrownBy(() -> service.send(message("user@example.com", "主题", "正文")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮件服务未配置");
    }

    @Test
    @DisplayName("消息为 null：抛参数异常")
    void send_nullMessage_throws() {
        assertThatThrownBy(() -> service.send(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("收件人为空：抛参数异常")
    void send_blankTo_throws() {
        assertThatThrownBy(() -> service.send(message("  ", "主题", "正文")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收件人");
    }

    @Test
    @DisplayName("SMTP 发送失败：转换为业务异常上抛（不静默吞掉）")
    void send_mailException_throwsBusinessException() {
        doThrow(new MailSendException("SMTP 连接失败")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.send(message("user@example.com", "主题", "正文")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮件发送失败");
    }
}
