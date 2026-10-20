package com.example.admin.infrastructure.mail;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 邮件消息（值对象）：与具体发送通道解耦，便于未来换服务商（SMTP / API 网关 / 队列）时只改实现。
 *
 * <p>为什么把"收件人/主题/正文"抽成值对象而不是散成方法参数：
 * 邮件参数天然成组，散成 5~6 个参数既不便于校验，也不便于在调用链路上传递与记录。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
public class EmailMessage {

    /** 收件人（单个或多个，多个用逗号分隔亦可） */
    private final String to;

    /** 抄送（可为空） */
    private final List<String> cc;

    /** 密送（可为空） */
    private final List<String> bcc;

    /** 主题 */
    private final String subject;

    /** 正文：纯文本或 HTML（由 {@link #html} 决定） */
    private final String text;

    /** 正文是否为 HTML */
    private final boolean html;
}
