package com.example.admin.infrastructure.schedule;

import com.example.admin.application.service.StatementReminderAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 月度对账提醒定时任务（试点批 R3）。
 *
 * <p>每月 1 日 08:00（Asia/Shanghai）触发；业务在
 * {@link StatementReminderAppService} 内部按 {@code sys_config} 标记幂等，
 * 当天重复触发（服务重启）不会重复发信。
 *
 * <p>默认关闭（{@code admin.statement-reminder.enabled}），仅生产配置开启；
 * dev / staging 的邮件通道本就未配置，避免开发环境定时发信。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "admin.statement-reminder.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MonthlyStatementReminderJob {

    private final StatementReminderAppService reminderService;

    /**
     * 每月 1 日 08:00 发送上月对账待办提醒。
     */
    @Scheduled(cron = "0 0 8 1 * *", zone = "Asia/Shanghai")
    public void run() {
        try {
            reminderService.sendMonthlyReminders(LocalDate.now());
        } catch (Exception e) {
            // 定时任务绝不向上抛：调度器只会记一行错误，业务现场无人察觉
            log.error("月度对账提醒任务失败", e);
        }
    }
}
