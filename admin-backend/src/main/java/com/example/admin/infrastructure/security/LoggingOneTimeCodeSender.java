package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.auth.OneTimeCodeMessage;
import com.example.admin.domain.service.OneTimeCodeSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 一次性码发送实现（开发/默认通道）。
 *
 * <p>行为：把"已发送"事件打印到日志，便于本地联调与无人值守环境的自测。
 *
 * <p>为什么开发环境把码也回传给前端：
 * 避免在没人看日志的环境里"码发出去但谁也看不到"导致流程走不下去。
 * 该行为由配置 {@code admin.verify.reveal-code} 控制，**生产必须关闭**。
 *
 * <p>启用条件：{@code admin.verify.channel=log}（默认）；改为 {@code email} 时该实现不生效，
 * 由 {@link com.example.admin.infrastructure.mail.EmailOneTimeCodeSender} 接管。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "admin.verify.channel", havingValue = "log", matchIfMissing = true)
public class LoggingOneTimeCodeSender implements OneTimeCodeSender {

    /** 是否在响应中回传验证码（仅开发环境开启） */
    // 默认值必须是 false：类级默认值会在"未加载主配置"的场景生效（如单测、独立初始化），
    // 一旦为 true 就会把明文验证码写进日志文件，而该文件可被日志查看接口读到。
    @Value("${admin.verify.reveal-code:false}")
    private boolean revealCode;

    @Override
    public void send(OneTimeCodeMessage message) {
        // 明文验证码不写日志：日志文件会被"查看日志"接口读到，
        // 那里能看到日志的人通常远多于能看到验证码的人。
        // 开发需要拿到码时走响应回传（同一开关控制），不要把凭据沉淀到日志里。
        if (revealCode) {
            log.info("[开发模式] 用户 {} 的一次性验证码已生成（明文仅随响应返回，不写入日志）", message.getUsername());
        } else {
            log.info("已向用户 {} 发送一次性验证码", message.getUsername());
        }
    }

    @Override
    public boolean isRevealCode() {
        return revealCode;
    }
}
