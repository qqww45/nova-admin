package com.example.admin.application.service.impl;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.common.IdName;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdCustomerBrowseQuery;
import com.example.admin.domain.model.kd.KdLineBrowseQuery;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundBrowseQuery;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdDataRepository;
import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.KdCustomerPageQuery;
import com.example.admin.interfaces.dto.req.KdLinePageQuery;
import com.example.admin.interfaces.dto.req.KdOutboundPageQuery;
import com.example.admin.interfaces.dto.resp.KdOutboundLineDataResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 金蝶同步数据浏览应用服务测试。
 *
 * <p>回归点（与 KdStatementAppServiceImplTest 同源的不变量）：
 * <ul>
 *     <li>业务员（非超管）查询强制按本人 ownerUserId 隔离——即使伪造
 *         ownerUserId 参数也会被覆盖（方案 §5「所有查询强制带 owner_user_id」）；</li>
 *     <li>业务员展开他人单据分录必须拿不到（返回"不存在"，不暴露他人数据）；</li>
 *     <li>分录页回填单据编号只认「账套 + FID」——跨账套同 FID 不得串号；</li>
 *     <li>金额按「分」存储、按「元」输出；数量 milli → 3 位小数；单价缺失按金额/数量反推。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
class KdDataAppServiceImplTest {

    @Mock
    private KdDataRepository dataRepository;

    @Mock
    private KdBindingRepository bindingRepository;

    @Mock
    private BizUserRepository bizUserRepository;

    @Mock
    private OperatorContext operatorContext;

    @InjectMocks
    private KdDataAppServiceImpl service;

    @Test
    @DisplayName("业务员查询必须强制本人隔离，伪造的 ownerUserId 参数被覆盖")
    void customerPageShouldForceOwnerScopeForSalesperson() {
        when(operatorContext.current()).thenReturn(user("common"));
        when(operatorContext.currentUserId()).thenReturn(9L);
        when(dataRepository.pageCustomers(any())).thenReturn(emptyPage());

        KdCustomerPageQuery query = new KdCustomerPageQuery();
        query.setOwnerUserId("1"); // 伪造超管 ID，必须被覆盖为本人
        service.customerPage(query);

        ArgumentCaptor<KdCustomerBrowseQuery> captor =
                ArgumentCaptor.forClass(KdCustomerBrowseQuery.class);
        verify(dataRepository).pageCustomers(captor.capture());
        assertEquals(9L, captor.getValue().getOwnerUserId(), "业务员只能看自己上报的数据");
    }

    @Test
    @DisplayName("管理员不传 ownerUserId 时看全部，日期边界为「含当天」")
    void outboundPageShouldAllowAdminToSeeAll() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(dataRepository.pageOutbounds(any())).thenReturn(pageOf());

        KdOutboundPageQuery query = new KdOutboundPageQuery();
        query.setDateFrom("2026-10-01");
        query.setDateTo("2026-10-31");
        service.outboundPage(query);

        ArgumentCaptor<KdOutboundBrowseQuery> captor =
                ArgumentCaptor.forClass(KdOutboundBrowseQuery.class);
        verify(dataRepository).pageOutbounds(captor.capture());
        assertEquals(null, captor.getValue().getOwnerUserId(), "管理员默认不限归属人");
        assertEquals(LocalDateTime.parse("2026-10-01T00:00:00"), captor.getValue().getBizFrom());
        // 止条件是次日 00:00：10-31 当天必须被包含
        assertEquals(LocalDateTime.parse("2026-11-01T00:00:00"), captor.getValue().getBizTo());
    }

    @Test
    @DisplayName("管理员可显式按业务员过滤（ownerUserId 参数生效）")
    void outboundPageShouldHonorOwnerFilterForAdmin() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(dataRepository.pageOutbounds(any())).thenReturn(pageOf());

        KdOutboundPageQuery query = new KdOutboundPageQuery();
        query.setOwnerUserId("9");
        service.outboundPage(query);

        ArgumentCaptor<KdOutboundBrowseQuery> captor =
                ArgumentCaptor.forClass(KdOutboundBrowseQuery.class);
        verify(dataRepository).pageOutbounds(captor.capture());
        assertEquals(9L, captor.getValue().getOwnerUserId());
    }

    @Test
    @DisplayName("业务员展开他人出库单分录必须拿不到（不暴露他人数据存在性）")
    void outboundLinesShouldHideOthersBillsForSalesperson() {
        when(operatorContext.current()).thenReturn(user("common"));
        when(operatorContext.currentUserId()).thenReturn(9L);
        when(dataRepository.findBillById(5001L)).thenReturn(bill(5001L, 77L, "ACCT_A"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.outboundLines("5001"));
        assertTrue(ex.getMessage().contains("不存在"), "应返回「不存在」而非「无权限」");
    }

    @Test
    @DisplayName("管理员展开分录：金额分转元、数量千分位、单价缺失按金额/数量反推")
    void outboundLinesShouldConvertUnits() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(dataRepository.findBillById(5001L)).thenReturn(bill(5001L, 9L, "ACCT_A"));
        KdOutboundLine raw = line("ACCT_A", 5001L, 90001L, 123456L, 1500L, 0L);
        when(dataRepository.listLinesOfBill(isNull(), eq("ACCT_A"), eq(5001L)))
                .thenReturn(List.of(raw));

        List<KdOutboundLineDataResp> resp = service.outboundLines("5001");

        assertEquals(1, resp.size());
        assertEquals(new BigDecimal("1234.56"), resp.get(0).getAmount(), "123456 分 → 1234.56 元");
        assertEquals(new BigDecimal("1.500"), resp.get(0).getQty(), "1500 milli → 1.500");
        // 单价为 0：按金额 / 数量反推（与对账单同口径，4 位小数）
        assertEquals(new BigDecimal("823.0400"), resp.get(0).getPrice());
    }

    @Test
    @DisplayName("分录页回填单据编号只认「账套 + FID」：跨账套同 FID 不串号")
    void linePageShouldEnrichBillNoWithinSameAcct() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(dataRepository.pageLines(any())).thenReturn(pageOf(
                line("ACCT_A", 5001L, 90001L, 100L, 1000L, 100L),
                line("ACCT_B", 5001L, 90002L, 200L, 1000L, 200L)));
        // 两个账套都有 FID=5001 的单，单号不同
        KdOutbound billA = bill(5001L, 9L, "ACCT_A");
        billA.setBillNo("OUT-A");
        KdOutbound billB = bill(5001L, 9L, "ACCT_B");
        billB.setBillNo("OUT-B");
        when(dataRepository.findBillsByFids(isNull(), anyMap())).thenReturn(List.of(billA, billB));

        KdLinePageQuery query = new KdLinePageQuery();
        List<KdOutboundLineDataResp> records = service.linePage(query).getRecords();

        assertEquals("OUT-A", records.get(0).getBillNo(), "ACCT_A 的分录应回填 OUT-A");
        assertEquals("OUT-B", records.get(1).getBillNo(), "ACCT_B 的分录应回填 OUT-B");
    }

    @Test
    @DisplayName("业务员下拉（owners）只允许管理员调用")
    void ownersShouldRejectSalesperson() {
        when(operatorContext.current()).thenReturn(user("common"));

        assertThrows(BusinessException.class, () -> service.owners());
    }

    @Test
    @DisplayName("owners 以 kd_binding 为准回填姓名")
    void ownersShouldListBindingUsers() {
        when(operatorContext.current()).thenReturn(user("admin"));
        com.example.admin.domain.model.kd.KdBinding binding =
                new com.example.admin.domain.model.kd.KdBinding();
        binding.setUserId(9L);
        binding.setAcctId("ACCT_A");
        com.example.admin.domain.model.kd.KdBinding other =
                new com.example.admin.domain.model.kd.KdBinding();
        other.setUserId(9L);
        other.setAcctId("ACCT_B");
        when(bindingRepository.listByUser(isNull())).thenReturn(List.of(binding, other));
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of(new IdName(9L, "张三")));

        List<com.example.admin.interfaces.dto.resp.KdOwnerResp> owners = service.owners();

        assertEquals(1, owners.size(), "同人两个账套只出现一次");
        assertEquals("9", owners.get(0).getId());
        assertEquals("张三", owners.get(0).getName());
    }

    /* ---------------- 工具 ---------------- */

    private static LoginUser user(String roleKey) {
        LoginUser user = new LoginUser();
        user.setUserId(9L);
        user.setRoles(java.util.Set.of(roleKey));
        return user;
    }

    private static KdOutbound bill(Long fid, Long ownerUserId, String acctId) {
        KdOutbound bill = new KdOutbound();
        bill.setId(fid);
        bill.setOwnerUserId(ownerUserId);
        bill.setAcctId(acctId);
        bill.setKdFid(fid);
        bill.setBillNo("OUT-" + fid);
        bill.setInvalid(0);
        bill.setCancelled(0);
        return bill;
    }

    private static KdOutboundLine line(String acctId, Long outboundFid, Long entryId,
                                       Long amountCents, Long qtyMilli, Long priceCents) {
        KdOutboundLine line = new KdOutboundLine();
        line.setId(entryId);
        line.setOwnerUserId(9L);
        line.setAcctId(acctId);
        line.setOutboundFid(outboundFid);
        line.setEntryId(entryId);
        line.setMaterialNumber("M" + entryId);
        line.setMaterialName("物料" + entryId);
        line.setUnit("PCS");
        line.setAmountCents(amountCents);
        line.setQtyMilli(qtyMilli);
        line.setPriceCents(priceCents);
        line.setInvalid(0);
        return line;
    }

    @SafeVarargs
    private static <T> PageResult<T> pageOf(T... records) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(List.of(records));
        page.setTotal(records.length);
        page.setPageNum(1);
        page.setPageSize(10);
        page.setPages(1);
        return page;
    }

    private static PageResult<KdCustomer> emptyPage() {
        PageResult<KdCustomer> page = new PageResult<>();
        page.setRecords(List.of());
        page.setTotal(0);
        page.setPageNum(1);
        page.setPageSize(10);
        page.setPages(0);
        return page;
    }
}
