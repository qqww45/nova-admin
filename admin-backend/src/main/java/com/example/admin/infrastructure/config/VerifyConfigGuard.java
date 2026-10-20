package com.example.admin.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境人机验证配置守卫。
 *
 * <p>为什么需要它（而不是只靠"记得改配置"）：
 * 这类开关的特点是<b>配错了系统照样能跑</b>——
 * 验证码被打印到日志、被回传给前端，用户能登录、功能"看起来正常"，
 * 直到有人从响应里直接读到验证码才发现强验证早已形同虚设。
 * 所以这里让"配错 = 起不来"：错误的配置在启动阶段就暴露，而不是留到线上慢慢发现。
 *
 * <p>为什么用 {@code @PostConstruct} 抛异常而不是打印警告：
 * 警告会被日志淹没；启动失败无法被忽略。
 *
 * <p>为什么只在 prod profile 生效：
 * 开发环境需要便利（日志通道 + 回传验证码），不应对其施加生产约束。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
// 不再只绑 prod：只要不是开发 profile 就要检查。
// 否则"运维忘设 SPRING_PROFILES_ACTIVE 而跑在 dev"时守卫完全不加载，
// 而 dev 配置恰好是验证码回传 + 日志通道 —— 第二因子等于不存在。
//
// staging 例外：上线过渡环境没有邮件服务，只能走日志通道 + 回传验证码，
// 目的是先把系统跑起来验收。该 profile 不承载真实数据，正式上线必须切回 prod，
// 届时本守卫重新生效，配置不安全会直接拒绝启动。
@Profile("!dev & !staging")
public class VerifyConfigGuard {

    /** 是否回传验证码：生产必须为 false */
    @Value("${admin.verify.reveal-code:false}")
    private boolean revealCode;

    /** 下发通道：生产必须为 email（或实现了真实送达能力的通道） */
    @Value("${admin.verify.channel:email}")
    private String channel;

    /** 邮件服务地址 */
    @Value("${spring.mail.host:}")
    private String mailHost;

    /** 邮件账号 */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    /** 邮件密码/授权码 */
    @Value("${spring.mail.password:}")
    private String mailPassword;

    /**
     * 启动时校验：任一项不安全即终止启动。
     */
    @PostConstruct
    public void check() {
        List<String> problems = new ArrayList<>();
        if (revealCode) {
            problems.add("admin.verify.reveal-code=true —— 验证码会随响应返回给前端，强验证形同虚设，必须设为 false");
        }
        if ("log".equalsIgnoreCase(channel)) {
            problems.add("admin.verify.channel=log —— 日志通道不会把验证码送达用户，生产必须设为 email");
        }
        // 邮件三项无条件检查：生产环境必然要用邮件通道（log 本身就是错误配置），
        // 若在 channel=log 时跳过检查，运维改成 email 后还得再启动一次才会发现邮件没配——
        // 一次报全，能省掉一轮重启。
        if (mailHost.isBlank()) {
            problems.add("spring.mail.host 未配置（环境变量 MAIL_HOST）—— 邮件发不出去，用户将卡在验证环节");
        }
        if (mailUsername.isBlank()) {
            problems.add("spring.mail.username 未配置（环境变量 MAIL_USERNAME）");
        }
        if (mailPassword.isBlank()) {
            problems.add("spring.mail.password 未配置（环境变量 MAIL_PASSWORD，多数邮件商填授权码）");
        }
        if (!problems.isEmpty()) {
            String detail = String.join("\n  - ", problems);
            // 记录到日志后再抛：容器里有时只能看到部分输出，两条渠道都留痕
            log.error("生产环境人机验证配置不安全，已阻止启动：\n  - {}", detail);
            throw new IllegalStateException("生产环境人机验证配置不安全，已阻止启动：\n  - " + detail);
        }
        log.info("人机验证配置校验通过：channel={} revealCode={} mailHost={}", channel, revealCode, mailHost);
    }
}
