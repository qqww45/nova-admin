package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.OneTimeCodeMessage;

/**
 * 一次性码发送端口（强验证用）。
 *
 * <p>为什么做成端口而不是直接在业务里发邮件/短信：
 * 发送通道是外部依赖（邮件服务、短信网关、企业 IM），属于基础设施；
 * 端口化之后，业务只依赖"把码送到人手里"这件事，
 * 换通道（邮件 → 短信 → 企业微信）不需要改动任何业务代码。
 *
 * <p>通道实现：
 * <ul>
 *     <li>开发环境：{@code LoggingOneTimeCodeSender}，打印到日志并按 {@code admin.verify.reveal-code} 决定是否回传前端；</li>
 *     <li>生产环境：{@code EmailOneTimeCodeSender}，通过 SMTP 发送邮件（由 {@code admin.verify.channel=email} 启用）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface OneTimeCodeSender {

    /**
     * 把一次性码发到用户可触达的渠道。
     *
     * <p>实现可自行选择要使用的字段（邮件实现看 email，短信实现看 phone）；
     * 都为空时应抛出业务异常，提示管理员或运营补全联系方式。
     *
     * @param message 下发消息
     */
    void send(OneTimeCodeMessage message);

    /**
     * 是否把验证码回传给前端（仅开发模式用，便于自测）。
     *
     * @return true 表示开发模式
     */
    default boolean isRevealCode() {
        return false;
    }
}
