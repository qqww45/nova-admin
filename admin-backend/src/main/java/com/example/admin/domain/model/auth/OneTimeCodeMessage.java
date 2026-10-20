package com.example.admin.domain.model.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 一次性码下发消息（值对象）：不直接暴露 User 实体，避免 domain 层依赖用户表的更多字段。
 *
 * <p>通道实现（email/sms/IM）只取自己需要的字段，互不耦合。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
public class OneTimeCodeMessage {

    /** 用户名（用于日志与回执） */
    private final String username;

    /** 一次性验证码（明文，只在生成这一次传递给 sender） */
    private final String code;

    /** 邮箱（邮件通道使用；其他通道忽略） */
    private final String email;

    /** 手机号（短信通道使用；其他通道忽略） */
    private final String phone;
}
