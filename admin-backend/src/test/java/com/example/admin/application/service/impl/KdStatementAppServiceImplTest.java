package com.example.admin.application.service.impl;

import com.example.admin.application.service.KdStatementExport;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.ConfigRepository;
import com.example.admin.domain.repository.KdStatementRepository;
import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.KdStatementQuery;
import com.example.admin.interfaces.dto.resp.KdStatementHealthResp;
import com.example.admin.interfaces.dto.resp.KdStatementSummaryResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
 *     <li>明细查询：金蝶客户内码只在账套内唯一，同内码跨账套必须显式拒绝而不是串户合并；</li>
 *     <li>导出：金额按「分」存储、按「元」写出；数量 × 单价写公式（模板口径）；
 *         无分录单据退回表头金额直填；</li>
 *     <li>导出超过 5000 行必须显式报错——对账单是给客户签字的核数依据，
 *         静默截断会让导出文件与页面汇总对不上且无人察觉；</li>
 *     <li>导出文件名：单客户带客户名，多客户用「金蝶对账单」前缀；</li>
 *     <li>汇总行回填金蝶客户编码——重名客户靠编码识别（防串户）；</li>
 *     <li>数据体检：无分录 / 作废 / 失效三类计数正确，跨账套同 FID 不串单。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KdStatementAppServiceImplTest {

    @Mock
    private KdStatementRepository statementRepository;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private BizUserRepository bizUserRepository;

    @Mock
    private OperatorContext operatorContext;

    @InjectMocks
    private KdStatementAppServiceImpl service;

    /**
     * summary / detail / health 都会批量回查客户编码；默认返回空映射，
     * 编码相关用例覆写具体值。LENIENT 下未走到的 stub 也不报错。
     */
    @BeforeEach
    void stubCustomerNumbers() {
        when(statementRepository.findCustomerNumbers(any(), anyList())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("业务员（非超管）查询必须按本人 ownerUserId 隔离")
    void summaryShouldScopeToOwnerForSalesperson() {
        when(operatorContext.current()).thenReturn(user("common"));
        when(operatorContext.currentUserId()).thenReturn(9L);
        when(statementRepository.listBills(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.summary(query("2026-10-01", "2026-10-31"));

        org.mockito.ArgumentCaptor<Long> ownerCaptor = org.mockito.ArgumentCaptor.forClass(Long.class);
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
    @DisplayName("同客户内码命中多个账套时，明细必须拒绝而不是串户合并")
    void detailShouldRejectCrossAcctFid() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(statementRepository.listBills(any(), any(), any(), any(), any())).thenReturn(List.of(
                bill(5001L, 1001L, "深圳示例客户A", "2026-10-10T00:00:00", "ACCT_A"),
                bill(5002L, 1001L, "东莞示例客户B", "2026-10-11T00:00:00", "ACCT_B")));

        KdStatementQuery query = query("2026-10-01", "2026-10-31");
        query.setCustomerFid("1001");

        BusinessException e = assertThrows(BusinessException.class, () -> service.detail(query));
        assertTrue(e.getMessage().contains("多个账套"), "应提示指定账套：" + e.getMessage());
    }

    @Test
    @DisplayName("导出：数量/单价按分转元写出，金额列为「数量 × 单价」公式（模板口径）")
    void exportShouldRenderCentsAsYuanAndFormulaAmount() throws IOException {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of());
        when(statementRepository.listBills(any(), any(), any(), any(), any()))
                .thenReturn(List.of(bill(5001L, 1001L, "深圳示例客户", "2026-10-10T00:00:00", "ACCT_A")));
        when(statementRepository.listLines(any(), anyList()))
                .thenReturn(List.of(line(5001L, 90001L, 123456L, 11000L, 43200L)));

        KdStatementExport file = service.export(query("2026-10-01", "2026-10-31"));

        String xml = sheetXml(file.content(), 1);
        assertTrue(file.fileName().startsWith("对账单-深圳示例客户-"), "单客户导出应带客户名：" + file.fileName());
        assertTrue(xml.contains("<v>11</v>"), "数量 11000 milli 应写出为 11：" + xml);
        assertTrue(xml.contains("<v>432</v>"), "单价 43200 分应写出为 432：" + xml);
        assertTrue(xml.contains("<f>H8*I8</f>"), "金额列应写「数量 × 单价」公式：" + xml);
        assertTrue(xml.contains("<f>SUM(J8:J8)</f>"), "页脚货款合计应为 SUM 公式：" + xml);
        assertTrue(xml.contains("<f>J9-G10</f>"), "应收 = 货款 − 已收：" + xml);
    }

    @Test
    @DisplayName("导出：无分录单据退回表头金额直填并标注")
    void exportShouldFallbackToHeaderAmountWhenNoLines() throws IOException {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of());
        // 单据头金额 123456 分 → 1234.56 元；无分录时 J 列直填值而不是公式
        KdOutbound bill = bill(5001L, 1001L, "深圳示例客户", "2026-10-10T00:00:00", "ACCT_A");
        bill.setAmountCents(123456L);
        when(statementRepository.listBills(any(), any(), any(), any(), any()))
                .thenReturn(List.of(bill));
        when(statementRepository.listLines(any(), anyList())).thenReturn(List.of());

        String xml = sheetXml(service.export(query("2026-10-01", "2026-10-31")).content(), 1);

        assertTrue(xml.contains("<v>1234.56</v>"), "无分录单据金额应直填 1234.56 元：" + xml);
        assertTrue(xml.contains("（该单无分录数据）"), "无分录单据应标注说明：" + xml);
    }

    @Test
    @DisplayName("导出：多个客户按物料分组拆 sheet，物料号作 sheet 名")
    void exportShouldSplitSheetsByCustomerAndMaterial() throws IOException {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of());
        when(statementRepository.listBills(any(), any(), any(), any(), any())).thenReturn(List.of(
                bill(5001L, 1001L, "深圳示例客户", "2026-10-10T00:00:00", "ACCT_A"),
                bill(5002L, 2002L, "东莞示例客户", "2026-10-11T00:00:00", "ACCT_A")));
        when(statementRepository.listLines(any(), anyList())).thenReturn(List.of(
                line(5001L, 90001L, 100L, 1000L, 100L, "MAT-A"),
                line(5002L, 90002L, 100L, 1000L, 100L, "MAT-B")));

        KdStatementExport file = service.export(query("2026-10-01", "2026-10-31"));

        String workbook = part(file.content(), "xl/workbook.xml");
        assertTrue(workbook.contains("MAT-A"), "sheet 应按物料命名：" + workbook);
        assertTrue(workbook.contains("MAT-B"), "sheet 应按物料命名：" + workbook);
        assertTrue(file.fileName().startsWith("金蝶对账单-"), "多客户导出用通用前缀：" + file.fileName());
    }

    @Test
    @DisplayName("导出：超过 5000 行必须显式报错，绝不静默截断")
    void exportShouldRejectWhenLinesExceedCap() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of());
        when(statementRepository.listBills(any(), any(), any(), any(), any()))
                .thenReturn(List.of(bill(5001L, 1001L, "深圳示例客户", "2026-10-10T00:00:00", "ACCT_A")));
        // 6000 行：报错信息必须是全量口径（6000），而不是停在 5000
        List<KdOutboundLine> lines = new ArrayList<>();
        for (long i = 1; i <= 6000; i++) {
            lines.add(line(5001L, i, 100L, 1000L, 100L));
        }
        when(statementRepository.listLines(any(), anyList())).thenReturn(lines);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.export(query("2026-10-01", "2026-10-31")));
        assertTrue(e.getMessage().contains("共 6000 行"), "报错应说明全量行数：" + e.getMessage());
        assertTrue(e.getMessage().contains("5000"), "报错应说明上限：" + e.getMessage());
    }

    @Test
    @DisplayName("导出：范围内无数据必须报错，不生成空对账单")
    void exportShouldRejectWhenNoData() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(statementRepository.listBills(any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.export(query("2026-10-01", "2026-10-31")));
    }

    /* ---------------- 工具 ---------------- */


    @Test
    @DisplayName("汇总行必须回填金蝶客户编码（重名客户识别）")
    void summaryShouldCarryCustomerNumber() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(statementRepository.listBills(any(), any(), any(), any(), any()))
                .thenReturn(List.of(bill(5001L, 1001L, "深圳示例客户", "2026-10-10T00:00:00", "ACCT_A")));
        when(statementRepository.findCustomerNumbers(any(), anyList()))
                .thenReturn(Map.of("ACCT_A|1001", "CUST-001"));

        PageResult<KdStatementSummaryResp> page = service.summary(query("2026-10-01", "2026-10-31"));

        assertEquals(1, page.getRecords().size());
        assertEquals("CUST-001", page.getRecords().get(0).getCustomerNumber(),
                "汇总行应回填金蝶客户编码");
    }

    @Test
    @DisplayName("数据体检：无分录 / 作废 / 失效三类计数必须正确")
    void healthShouldCountExcludedBills() {
        when(operatorContext.current()).thenReturn(user("admin"));
        KdOutbound cancelledBill = bill(5003L, 1001L, "深圳示例客户", "2026-10-03T00:00:00", "ACCT_A");
        cancelledBill.setCancelled(1);
        KdOutbound invalidBill = bill(5004L, 1001L, "深圳示例客户", "2026-10-04T00:00:00", "ACCT_A");
        invalidBill.setInvalid(1);
        // 4 张单：1 张有效有分录、1 张有效无分录（退回表头金额）、1 张作废、1 张失效
        when(statementRepository.listAllBillsIncludingExcluded(any(), any(), any(), any()))
                .thenReturn(List.of(
                        bill(5001L, 1001L, "深圳示例客户", "2026-10-01T00:00:00", "ACCT_A"),
                        bill(5002L, 1001L, "深圳示例客户", "2026-10-02T00:00:00", "ACCT_A"),
                        cancelledBill,
                        invalidBill));
        when(statementRepository.listLines(any(), anyList()))
                .thenReturn(List.of(line(5001L, 90001L, 100L, 1000L, 100L)));

        KdStatementHealthResp resp = service.health(query("2026-10-01", "2026-10-31"));

        assertEquals(2, resp.getBillCount(), "有效单应为 2 张");
        assertEquals(1, resp.getNoLineCount(), "无分录单应为 1 张（对账单退回表头金额）");
        assertEquals(1, resp.getCancelledCount(), "作废单应为 1 张");
        assertEquals(1, resp.getInvalidCount(), "失效单应为 1 张");
        assertTrue(!resp.isHealthy(), "存在被排除单据时不应判为健康");
    }

    @Test
    @DisplayName("数据体检：有效单的分录必须按账套+FID 关联，跨账套同 FID 不串单")
    void healthShouldNotMixLinesAcrossAccts() {
        when(operatorContext.current()).thenReturn(user("admin"));
        KdOutbound b = bill(5001L, 1002L, "客户B", "2026-10-01T00:00:00", "ACCT_B");
        // 只有 ACCT_B 的 5001 有分录；若按裸 FID 关联，ACCT_A 会被误判为有分录
        KdOutboundLine bLine = line(5001L, 90001L, 100L, 1000L, 100L);
        bLine.setAcctId("ACCT_B");
        when(statementRepository.listAllBillsIncludingExcluded(any(), any(), any(), any()))
                .thenReturn(List.of(
                        bill(5001L, 1001L, "客户A", "2026-10-01T00:00:00", "ACCT_A"), b));
        when(statementRepository.listLines(any(), anyList())).thenReturn(List.of(bLine));

        KdStatementHealthResp resp = service.health(query("2026-10-01", "2026-10-31"));

        assertEquals(2, resp.getBillCount());
        assertEquals(1, resp.getNoLineCount(), "ACCT_A 的 5001 应计为无分录，不能被 ACCT_B 的同 FID 分录串掉");
    }

    private static KdStatementQuery query(String from, String to) {
        KdStatementQuery query = new KdStatementQuery();
        query.setDateFrom(from);
        query.setDateTo(to);
        return query;
    }

    private static LoginUser user(String roleKey) {
        LoginUser user = new LoginUser();
        user.setUserId(9L);
        user.setNickname("测试用户");
        user.setRoles(Set.of(roleKey));
        return user;
    }

    private static KdOutbound bill(Long fid, Long customerFid, String customerName, String bizDate, String acctId) {
        KdOutbound bill = new KdOutbound();
        bill.setAcctId(acctId);
        bill.setKdFid(fid);
        bill.setOwnerUserId(9L);
        bill.setCustomerFid(customerFid);
        bill.setCustomerName(customerName);
        bill.setBillNo("OUT-" + fid);
        bill.setBizDate(LocalDateTime.parse(bizDate));
        bill.setCancelled(0);
        bill.setInvalid(0);
        bill.setAmountCents(0L);
        return bill;
    }

    private static KdOutboundLine line(Long outboundFid, Long entryId, Long amountCents,
                                       Long qtyMilli, Long priceCents) {
        return line(outboundFid, entryId, amountCents, qtyMilli, priceCents, "MAT-1");
    }

    private static KdOutboundLine line(Long outboundFid, Long entryId, Long amountCents,
                                       Long qtyMilli, Long priceCents, String materialNumber) {
        KdOutboundLine line = new KdOutboundLine();
        line.setAcctId("ACCT_A");
        line.setOutboundFid(outboundFid);
        line.setEntryId(entryId);
        line.setMaterialNumber(materialNumber);
        line.setMaterialName("物料" + entryId);
        line.setSpec("规格" + entryId);
        line.setUnit("pcs");
        line.setQtyMilli(qtyMilli);
        line.setPriceCents(priceCents);
        line.setAmountCents(amountCents);
        line.setInvalid(0);
        return line;
    }

    /** 取 zip 里的指定零件文本 */
    private static String part(byte[] xlsxBytes, String name) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException(name + " 不存在");
    }

    /** 取第 n 个 sheet 的 XML 文本（1 起） */
    private static String sheetXml(byte[] xlsxBytes, int sheetNo) throws IOException {
        return part(xlsxBytes, "xl/worksheets/sheet" + sheetNo + ".xml");
    }
}
