package com.example.admin.application.service;

import java.time.LocalDate;

/**
 * 月度对账提醒应用服务（试点批 R3）。
 *
 * <p>痛点：月底容易忘——忘了出对账单、忘了催客户回签。本服务每月 1 日
 * 给有上月对账待办的业务员发邮件：待签发（草稿）与已签发待客户确认的清单。
 *
 * @author admin-system
 * @since 2026-11-03
 */
public interface StatementReminderAppService {

    /**
     * 发送上月对账待办提醒。
     *
     * <p>幂等：当日已成功发送过（按 {@code sys_config} 标记）直接返回 0，
     * 避免 1 号当天服务重启导致的重复轰炸。
     *
     * @param today 今天（便于测试注入）
     * @return 成功发送的邮件数（无邮箱 / 无待办 / 发送失败不计入）
     */
    int sendMonthlyReminders(LocalDate today);
}
