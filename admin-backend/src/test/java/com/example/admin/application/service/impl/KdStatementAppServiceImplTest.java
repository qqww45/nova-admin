package com.example.admin.application.service.impl;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.KdStatementRepository;
import com.example.admin.interfaces.dto.req.KdStatementQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 金蝶对账单应用服务测试。
 *
 * <p>回归点：
 * <ul>
 *     <li>业务员（非超管）查询必须按本人隔离——方案 §5「所有查询强制带 owner_user_id」；</li>
 *     <li>查询窗口上限 366 天；</li>
 *     <li>导出明细分录超出 5000 行上限时必须显式写截断标记——对账单是核数依据，
 *         静默截断会让导出文件与页面汇总对不上且无人察觉；</li>
 *     <li>金额按「分」存储、按「元」导出的换算（cents → 2 位小数）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
class KdStatementAppServiceImplTest {

    @Mock
    private KdStatementRepository statementRepository;

    @Mock
    private OperatorContext operatorContext;

    @InjectMocks
    private KdStatementAppServiceImpl service;

    @Test
    @DisplayName("业务员（非超管）查询必须按本人 ownerUserId 隔离")
    void summaryShouldScopeToOwnerForSalesperson() {
        when(operatorContext.current()).thenReturn(user("common"));
        when(operatorContext.currentUserId()).thenReturn(9L);
        when(statementRepository.listBills(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.summary(query("2026-10-01", "2026-10-31"));

        ArgumentCaptor<Long> ownerCaptor = ArgumentCaptor.forClass(Long.class);
        verify(statementRepository).listBills(ownerCaptor.capture(), isNull(),
                any(), any(), isNull());
        assertEquals(9L, ownerCaptor.getValue(), "业务员只能看到自己上报的数据");
    }

    @Test
    @DisplayName("超管查询不做 owner 隔离（可跨业务员）")
    void summaryShouldNotScopeOwnerForSuperAdmin() {
        when(operatorContext.current()).thenReturn(user("admin"));

        service.summary(query("2026-10-01", "2026-10-31"));

        verify(statementRepository).listBills(isNull(), isNull(),
                eq(LocalDateTime.parse("2026-10-01T00:00:00")),
                eq(LocalDateTime.parse("2026-11-01T00:00:00")), isNull());
    }

    @Test
    @DisplayName("查询区间超过 366 天必须拒绝")
    void summaryShouldRejectWindowBeyondOneYear() {
        when(operatorContext.current()).thenReturn(user("admin"));

        assertThrows(BusinessException.class,
                () -> service.summary(query("2025-01-01", "2026-11-01")));
    }

    @Test
    @DisplayName("导出金额必须按分转元换算（123456 分 → 1234.56）")
    void exportShouldRenderCentsAsYuan() throws IOException {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(statementRepository.listBills(any(), any(), any(), any(), any()))
                .thenReturn(List.of(bill(5001L, 1001L, "深圳示例客户", "2026-10-10T00:00:00")));
        when(statementRepository.listLines(any(), anyList()))
                .thenReturn(List.of(line(5001L, 90001L, 123456L)));

        String xml = sheetXml(service.export(query("2026-10-01", "2026-10-31")), 2);

        assertTrue(xml.contains("1234.56"), "分 → 元换算结果应出现在明细 sheet：" + xml);
    }

    @Test
    @DisplayName("导出明细超 5000 行时必须在两个 sheet 写明截断标记")
    void exportShouldMarkTruncationWhenLinesExceedCap() throws IOException {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(statementRepository.listBills(any(), any(), any(), any(), any()))
                .thenReturn(List.of(bill(5001L, 1001L, "深圳示例客户", "2026-10-10T00:00:00")));
        // 6000 行（不是 5001）：确认截断后计数不中断，"共 N 行"必须是全量口径
        List<KdOutboundLine> lines = new ArrayList<>();
        for (long i = 1; i <= 6000; i++) {
            lines.add(line(5001L, i, 100L));
        }
        when(statementRepository.listLines(any(), anyList())).thenReturn(lines);

        byte[] bytes = service.export(query("2026-10-01", "2026-10-31"));

        String detailXml = sheetXml(bytes, 2);
        String summaryXml = sheetXml(bytes, 1);
        assertTrue(detailXml.contains("导出已达 5000 行上限"), "明细 sheet 应写明截断原因");
        assertTrue(detailXml.contains("本期分录共 6000 行"), "截断后仍应统计全量行数，而不是停在第 5001 行");
        assertTrue(summaryXml.contains("仅含前 5000 行"), "客户汇总 sheet 也应有截断说明，解释两边数字为何对不上");
    }

    /* ---------------- 工具 ---------------- */

    private static KdStatementQuery query(String from, String to) {
        KdStatementQuery query = new KdStatementQuery();
        query.setDateFrom(from);
        query.setDateTo(to);
        return query;
    }

    private static LoginUser user(String roleKey) {
        LoginUser user = new LoginUser();
        user.setUserId(9L);
        user.setRoles(Set.of(roleKey));
        return user;
    }

    private static KdOutbound bill(Long fid, Long customerFid, String customerName, String bizDate) {
        KdOutbound bill = new KdOutbound();
        bill.setAcctId("ACCT_A");
        bill.setKdFid(fid);
        bill.setCustomerFid(customerFid);
        bill.setCustomerName(customerName);
        bill.setBillNo("OUT-" + fid);
        bill.setBizDate(LocalDateTime.parse(bizDate));
        bill.setCancelled(0);
        bill.setInvalid(0);
        bill.setAmountCents(0L);
        return bill;
    }

    private static KdOutboundLine line(Long outboundFid, Long entryId, Long amountCents) {
        KdOutboundLine line = new KdOutboundLine();
        line.setAcctId("ACCT_A");
        line.setOutboundFid(outboundFid);
        line.setEntryId(entryId);
        line.setMaterialNumber("M" + entryId);
        line.setMaterialName("物料" + entryId);
        line.setUnit("PCS");
        line.setQtyMilli(1000L);
        line.setPriceCents(100L);
        line.setAmountCents(amountCents);
        line.setInvalid(0);
        return line;
    }

    /** 取第 n 个 sheet 的 XML 文本（1 起） */
    private static String sheetXml(byte[] xlsxBytes, int sheetNo) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("xl/worksheets/sheet" + sheetNo + ".xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("sheet" + sheetNo + " 不存在");
    }
}
