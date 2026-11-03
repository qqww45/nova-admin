package com.example.admin.application.service.impl;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 月度对账提醒测试（试点批 R3）。
 *
 * <p>回归点：
 * <ul>
 *     <li>当日已发过（sys_config 标记）直接跳过——1 号重启不重复轰炸；</li>
 *     <li>无待办不发信、写标记；</li>
 *     <li>有待办按录入人分组，无邮箱用户跳过且不中断其它人；</li>
 *     <li>提醒内容包含客户名、单号、期末应收与金蝶对账提示。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
class StatementReminderAppServiceImplTest {

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private StatementReminderAppServiceImpl service;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Test
    @DisplayName("当日已发送过必须直接跳过（不查、不发、不更新标记）")
    void shouldSkipWhenAlreadySentToday() {
        when(configRepository.findByKey(StatementReminderAppServiceImpl.LAST_SENT_KEY))
                .thenReturn(Optional.of(config("2026-11-01")));

        int sent = service.sendMonthlyReminders(TODAY);

        assertEquals(0, sent);
        verify(statementRepository, never()).page(any());
        verify(emailService, never()).send(any());
        verify(configRepository, never()).update(any());
    }

    @Test
    @DisplayName("上月无待办：不发信，但仍写标记（当天重启也不会再查）")
    void shouldWriteMarkWhenNothingToDo() {
        when(configRepository.findByKey(StatementReminderAppServiceImpl.LAST_SENT_KEY))
                .thenReturn(Optional.empty());
        when(statementRepository.page(any(StatementQuery.class))).thenReturn(emptyPage());

        int sent = service.sendMonthlyReminders(TODAY);

        assertEquals(0, sent);
        verify(emailService, never()).send(any());
        ArgumentCaptor<ConfigItem> captor = ArgumentCaptor.forClass(ConfigItem.class);
        verify(configRepository).insert(captor.capture());
        assertEquals("2026-11-01", captor.getValue().getValue());
    }

    @Test
    @DisplayName("有待办：按录入人分组发信，无邮箱用户跳过不中断，内容含客户与金额")
    void shouldSendPerCreatorAndSkipUserlessEmail() {
        when(configRepository.findByKey(StatementReminderAppServiceImpl.LAST_SENT_KEY))
                .thenReturn(Optional.empty());
        // 两个状态各查一次：第一次 DRAFT（1 张，录入人 9），第二次 ISSUED（1 张，录入人 10）
        when(statementRepository.page(any(StatementQuery.class)))
                .thenReturn(pageOf(draft("ST-001", 5001L, 9L, "1234.56")))
                .thenReturn(pageOf(issued("ST-002", 5002L, 10L, "500.00")));
        when(customerRepository.findById(5001L)).thenReturn(customer(5001L, "深圳示例客户"));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user("zhang@ex.com")));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(null)));

        int sent = service.sendMonthlyReminders(TODAY);

        assertEquals(1, sent, "只有有邮箱的录入人收到（另一个跳过）");
        ArgumentCaptor<EmailMessage> mail = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(mail.capture());
        EmailMessage message = mail.getValue();
        assertEquals("zhang@ex.com", message.getTo());
        assertTrue(message.getSubject().contains("2026-10 月对账待办提醒"), "主题应为上月：" + message.getSubject());
        assertTrue(message.getText().contains("深圳示例客户"), "正文应含客户名");
        assertTrue(message.getText().contains("ST-001"), "正文应含单号");
        assertTrue(message.getText().contains("1234.56"), "正文应含期末应收");
        assertTrue(message.getText().contains("金蝶同步 → 对账单"), "应提示导出金蝶对账单核对出货口径");
        verify(configRepository).insert(any(ConfigItem.class));
    }

    /* ---------------- 工具 ---------------- */

    private static ConfigItem config(String value) {
        return ConfigItem.builder()
                .id(1L).key(StatementReminderAppServiceImpl.LAST_SENT_KEY).value(value)
                .type(ConfigType.STRING).builtin(false).remark("test").build();
    }

    private static <T> PageResult<T> emptyPage() {
        PageResult<T> page = new PageResult<>();
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        page.setPageNum(1);
        page.setPageSize(1000);
        page.setPages(0);
        return page;
    }

    private static <T> PageResult<T> pageOf(Statement... records) {
        PageResult<T> page = new PageResult<>();
        page.setRecords((List<T>) new ArrayList<>(List.of(records)));
        page.setTotal(records.length);
        page.setPageNum(1);
        page.setPageSize(1000);
        page.setPages(1);
        return page;
    }

    private static Statement draft(String stmtNo, Long customerId, Long createBy, String amount) {
        return statement(stmtNo, customerId, createBy, amount, StatementStatus.DRAFT);
    }

    private static Statement issued(String stmtNo, Long customerId, Long createBy, String amount) {
        return statement(stmtNo, customerId, createBy, amount, StatementStatus.ISSUED);
    }

    private static Statement statement(String stmtNo, Long customerId, Long createBy,
                                       String amount, StatementStatus status) {
        Statement statement = new Statement();
        statement.setStmtNo(stmtNo);
        statement.setCustomerId(customerId);
        statement.setCreateBy(createBy);
        statement.setEndingBalance(new BigDecimal(amount));
        statement.setStatus(status);
        return statement;
    }

    private static Customer customer(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerName(name);
        return customer;
    }

    private static User user(String email) {
        User user = new User();
        user.setId(9L);
        user.setEmail(email);
        return user;
    }
}
