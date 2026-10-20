package com.example.admin.infrastructure.mail;

/**
 * 邮件服务（基础设施端口）。
 *
 * <p>为什么单独抽一层，而不是业务里直接用 {@code JavaMailSender}：
 * <ul>
 *     <li>业务只关心"把信送到"，不关心 SMTP 细节；换服务商/换 API 网关时业务零改动；</li>
 *     <li>便于统一处理：发件人默认值、编码、失败语义、日志脱敏；</li>
 *     <li>便于测试：用例里注入 Mock 即可，不必起真实 SMTP。</li>
 * </ul>
 *
 * <p>当前实现见 {@link EmailServiceImpl}（Spring {@code JavaMailSender} + SMTP）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface EmailService {

    /**
     * 发送邮件。
     *
     * <p>失败策略：<b>不静默吞掉</b>——配置错误、授权码失效、网络异常都抛出业务异常，
     * 让调用方（以及最终用户）明确知道"没发出去"，而不是以为发成功了。
     *
     * @param message 邮件内容
     * @throws com.example.admin.common.exception.BusinessException 未配置 SMTP、参数非法或发送失败
     */
    void send(EmailMessage message);

    /**
     * SMTP 是否已配置（host 非空即视为已配置）。
     *
     * <p>用途：调用方在"非关键链路"（如通知类邮件）可先判断，
     * 避免未配置时抛异常打断主流程。
     *
     * @return true 表示已配置
     */
    boolean isConfigured();
}
