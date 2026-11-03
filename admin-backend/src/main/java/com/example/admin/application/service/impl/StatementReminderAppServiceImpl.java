package com.example.admin.application.service.impl;

import com.example.admin.application.service.StatementReminderAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.model.config.ConfigType;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.reconciliation.Statement;
import com.example.admin.domain.model.reconciliation.StatementQuery;
import com.example.admin.domain.model.reconciliation.StatementStatus;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.repository.ConfigRepository;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.StatementRepository;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.infrastructure.mail.EmailMessage;
import com.example.admin.infrastructure.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月度对账提醒服务实现。
 *
 * <p>口径：对账单的"期间止日"落在上个月的 {@code DRAFT}（还没签发）与
 * {@code ISSUED}（已签发待客户确认）单据，按录入人（createBy）分组发提醒。
 * KD 侧对账单是实时计算不落库，因此只做文字提示（导出金蝶对账单与客户核对）。
 *
 * <p>防重发：发送标记写 {@code sys_config}（key = {@link #LAST_SENT_KEY}），
 * 1 号当天服务重启不会重复轰炸。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementReminderAppServiceImpl implements StatementReminderAppService {

    /** 防重复发送标记（sys_config key）：值为 yyyy-MM-dd，当日已发即跳过 */
    static final String LAST_SENT_KEY = "statement.reminder.last-sent";

    /** 单封邮件里最多列出的单据行数：提醒不是报表，超长的去后台看 */
    private static final int MAX_LINES_PER_SECTION = 20;

    private final StatementRepository statementRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ConfigRepository configRepository;
    private final EmailService emailService;

    @Override
    public int sendMonthlyReminders(LocalDate today) {
        String todayText = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (todayText.equals(readMark())) {
            log.debug("月度对账提醒当日已发送，跳过");
            return 0;
        }

        // 上月窗口：按对账单"期间止日"过滤
        LocalDate monthStart = today.withDayOfMonth(1).minusMonths(1);
        LocalDateTime begin = monthStart.atStartOfDay();
        LocalDateTime end = today.withDayOfMonth(1).minusDays(1).atTime(LocalTime.MAX);

        List<Statement> drafts = listByStatus(StatementStatus.DRAFT.getCode(), begin, end);
        List<Statement> issued = listByStatus(StatementStatus.ISSUED.getCode(), begin, end);
        if (drafts.isEmpty() && issued.isEmpty()) {
            log.info("上月无对账待办，不发提醒");
            writeMark(todayText);
            return 0;
        }

        Map<Long, List<Statement>> byUser = groupByCreator(drafts, issued);
        String monthLabel = monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int sent = 0;
        for (Map.Entry<Long, List<Statement>> entry : byUser.entrySet()) {
            if (sendToOne(entry.getKey(), entry.getValue(), monthLabel)) {
                sent++;
            }
        }
        writeMark(todayText);
        log.info("月度对账提醒已发送：{}/{} 人（{} 月待办 草稿 {} 张、待确认 {} 张）",
                sent, byUser.size(), monthLabel, drafts.size(), issued.size());
        return sent;
    }

    /* ---------------- 内部方法 ---------------- */

    private List<Statement> listByStatus(String status, LocalDateTime begin, LocalDateTime end) {
        StatementQuery query = new StatementQuery();
        query.setStatus(status);
        query.setBeginDate(begin);
        query.setEndDate(end);
        query.setPageNum(1);
        query.setPageSize(1000);
        PageResult<Statement> page = statementRepository.page(query);
        return page.getRecords();
    }

    private Map<Long, List<Statement>> groupByCreator(List<Statement> drafts, List<Statement> issued) {
        Map<Long, List<Statement>> grouped = new LinkedHashMap<>();
        for (Statement statement : drafts) {
            if (statement.getCreateBy() != null) {
                grouped.computeIfAbsent(statement.getCreateBy(), k -> new ArrayList<>()).add(statement);
            }
        }
        for (Statement statement : issued) {
            if (statement.getCreateBy() != null) {
                grouped.computeIfAbsent(statement.getCreateBy(), k -> new ArrayList<>()).add(statement);
            }
        }
        return grouped;
    }

    /** 给单个业务员发提醒；无邮箱或发送失败返回 false（不中断其它人） */
    private boolean sendToOne(Long userId, List<Statement> statements, String monthLabel) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("用户 {} 无邮箱，跳过月度对账提醒", userId);
            return false;
        }
        Map<Long, String> customerNames = new LinkedHashMap<>();
        for (Statement statement : statements) {
            if (statement.getCustomerId() != null && !customerNames.containsKey(statement.getCustomerId())) {
                Customer customer = customerRepository.findById(statement.getCustomerId());
                if (customer != null) {
                    customerNames.put(customer.getId(), customer.getCustomerName());
                }
            }
        }

        StringBuilder body = new StringBuilder();
        body.append(String.format("%s 月对账待办如下（共 %d 张）：\n\n", monthLabel, statements.size()));
        appendSection(body, statements, StatementStatus.DRAFT, "一、待签发（还没发给客户确认）", customerNames);
        appendSection(body, statements, StatementStatus.ISSUED, "二、已签发、待客户回签确认", customerNames);
        body.append("\n请登录后台「对账单」页面处理；同时别忘了导出「金蝶同步 → 对账单」"
                + "与客户核对出货口径（金蝶侧金额以分录汇总为准）。\n");

        try {
            emailService.send(EmailMessage.builder()
                    .to(user.getEmail())
                    .subject(String.format("[衡账] %s 月对账待办提醒", monthLabel))
                    .text(body.toString())
                    .build());
            return true;
        } catch (Exception e) {
            // 通知类邮件：一个人失败不中断其它人，日志留痕
            log.warn("月度对账提醒发送失败：userId={} cause={}", userId, e.getMessage());
            return false;
        }
    }

    private void appendSection(StringBuilder body, List<Statement> statements, StatementStatus status,
                               String title, Map<Long, String> customerNames) {
        List<Statement> section = statements.stream()
                .filter(s -> s.getStatus() == status)
                .toList();
        if (section.isEmpty()) {
            return;
        }
        body.append(title).append("（").append(section.size()).append(" 张）：\n");
        section.stream().limit(MAX_LINES_PER_SECTION).forEach(statement ->
                body.append(String.format("  - %s %s 期末应收 %s 元%n",
                        customerNames.getOrDefault(statement.getCustomerId(), "客户"),
                        statement.getStmtNo(),
                        money(statement.getEndingBalance()))));
        if (section.size() > MAX_LINES_PER_SECTION) {
            body.append(String.format("  ……其余 %d 张请到后台查看%n", section.size() - MAX_LINES_PER_SECTION));
        }
        body.append('\n');
    }

    /** 读发送标记；从未发过返回空串 */
    private String readMark() {
        return configRepository.findByKey(LAST_SENT_KEY).map(ConfigItem::getValue).orElse("");
    }

    /** 写发送标记（key 不存在则新建） */
    private void writeMark(String value) {
        ConfigItem existing = configRepository.findByKey(LAST_SENT_KEY).orElse(null);
        if (existing == null) {
            configRepository.insert(ConfigItem.builder()
                    .key(LAST_SENT_KEY)
                    .value(value)
                    .type(ConfigType.STRING)
                    .builtin(false)
                    .remark("月度对账提醒最后发送日（自动写入）")
                    .build());
            return;
        }
        configRepository.update(ConfigItem.builder()
                .id(existing.getId())
                .key(existing.getKey())
                .value(value)
                .type(existing.getType())
                .builtin(existing.isBuiltin())
                .remark(existing.getRemark())
                .build());
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).toPlainString();
    }
}
