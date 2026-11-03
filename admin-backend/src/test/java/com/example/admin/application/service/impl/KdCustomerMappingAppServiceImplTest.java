package com.example.admin.application.service.impl;

import com.example.admin.application.service.KdCustomerMappingAppService;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdCustomerBrowseQuery;
import com.example.admin.domain.model.kd.KdCustomerMapping;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.KdCustomerMappingRepository;
import com.example.admin.domain.repository.KdDataRepository;
import com.example.admin.interfaces.dto.req.KdMappingSaveReq;
import com.example.admin.interfaces.dto.resp.KdCustomerMappingResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 金蝶客户 ↔ 内部客户映射测试（试点批 R4）。
 *
 * <p>回归点：
 * <ul>
 *     <li>隔离：业务员只看本人（忽略入参 ownerUserId），超管可跨人；</li>
 *     <li>自动建议：名称标准化唯一命中才建议，同名歧义跳过、已映射不覆盖；</li>
 *     <li>人工保存：业务员只能写本人映射，管理员代维护必须显式指定归属业务员。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
class KdCustomerMappingAppServiceImplTest {

    @Mock
    private KdCustomerMappingRepository mappingRepository;

    @Mock
    private KdDataRepository dataRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OperatorContext operatorContext;

    @InjectMocks
    private KdCustomerMappingAppServiceImpl service;

    @Test
    @DisplayName("业务员查询必须按本人隔离（忽略 ownerUserId 入参）")
    void listShouldScopeToOwnerForSalesperson() {
        when(operatorContext.current()).thenReturn(user("common"));
        when(operatorContext.currentUserId()).thenReturn(9L);
        when(dataRepository.pageCustomers(any())).thenReturn(pageOf());
        when(mappingRepository.listByOwner(9L)).thenReturn(List.of());
        when(customerRepository.listAll()).thenReturn(List.of());

        service.list(null, null, null, "66");

        ArgumentCaptor<KdCustomerBrowseQuery> captor =
                ArgumentCaptor.forClass(KdCustomerBrowseQuery.class);
        verify(dataRepository).pageCustomers(captor.capture());
        assertEquals(9L, captor.getValue().getOwnerUserId(), "业务员只能看自己的映射行");
    }

    @Test
    @DisplayName("自动建议：名称标准化唯一命中才建议，同名歧义与已映射跳过")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void autoSuggestShouldOnlyMatchUniqueNormalizedNames() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(dataRepository.pageCustomers(any())).thenReturn(pageOf(
                kd(1001L, "ACCT_A", "深圳示例客户"),   // 唯一命中 → 建议
                kd(1002L, "ACCT_A", "重名客户"),         // 内部两个同名 → 歧义跳过
                kd(1003L, "ACCT_A", "金蝶独有客户"),     // 无命中 → 跳过
                kd(1004L, "ACCT_A", "已映射客户")));      // 已有映射 → 不覆盖
        when(mappingRepository.listByOwner(isNull())).thenReturn(List.of(
                mapping(1004L, "ACCT_A", 5004L, KdCustomerMapping.TYPE_MANUAL)));
        when(customerRepository.listAll()).thenReturn(List.of(
                biz(5001L, "深圳示例客户"), biz(5002L, "重名客户"), biz(5003L, "重名客户")));

        KdCustomerMappingAppService.AutoSuggestResult result = service.autoSuggest(null, null);

        // 1001 规范化后唯一命中 5001；1002 命中 5002/5003 两个 → 歧义；1003 无命中；1004 已映射
        assertEquals(1, result.suggested(), "唯一命中才应生成建议");
        assertEquals(2, result.skipped(), "歧义与无命中应跳过（已映射不计 skipped）");
        ArgumentCaptor<List<KdCustomerMapping>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(mappingRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(5001L, captor.getValue().get(0).getBizCustomerId());
        assertEquals(KdCustomerMapping.TYPE_AUTO_SUGGEST, captor.getValue().get(0).getMatchType());
    }

    @Test
    @DisplayName("列表：建议/已确认/未匹配三态正确回填，内部客户被删时明示")
    void listShouldReportThreeStates() {
        when(operatorContext.current()).thenReturn(user("admin"));
        when(dataRepository.pageCustomers(any())).thenReturn(pageOf(
                kd(1001L, "ACCT_A", "未匹配客户"),
                kd(1002L, "ACCT_A", "建议中客户"),
                kd(1003L, "ACCT_A", "已确认客户")));
        when(mappingRepository.listByOwner(isNull())).thenReturn(List.of(
                mapping(1002L, "ACCT_A", 5002L, KdCustomerMapping.TYPE_AUTO_SUGGEST),
                mapping(1003L, "ACCT_A", 9999L, KdCustomerMapping.TYPE_MANUAL)));
        when(customerRepository.listAll()).thenReturn(List.of(biz(5002L, "内部建议客户")));

        List<KdCustomerMappingResp> rows = service.list(null, null, null, null);

        assertEquals(3, rows.size());
        assertEquals("UNMATCHED", rows.stream()
                .filter(r -> r.getKdFid().equals("1001")).findFirst().orElseThrow().getStatus());
        KdCustomerMappingResp suggested = rows.stream()
                .filter(r -> r.getKdFid().equals("1002")).findFirst().orElseThrow();
        assertEquals("SUGGESTED", suggested.getStatus());
        assertEquals("内部建议客户", suggested.getBizCustomerName());
        KdCustomerMappingResp confirmed = rows.stream()
                .filter(r -> r.getKdFid().equals("1003")).findFirst().orElseThrow();
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals("（内部客户不存在）", confirmed.getBizCustomerName(), "内部客户被删应明示而不是静默");
    }

    @Test
    @DisplayName("业务员保存映射只能写本人；管理员代维护必须显式指定归属业务员")
    void saveShouldEnforceOwnershipRules() {
        when(operatorContext.current()).thenReturn(user("common"));
        when(operatorContext.currentUserId()).thenReturn(9L);
        when(customerRepository.listAll()).thenReturn(List.of(biz(5001L, "客户")));
        when(mappingRepository.find(9L, "ACCT_A", 1001L)).thenReturn(null);

        service.save(saveReq("ACCT_A", 1001L, 5001L, "66"));

        ArgumentCaptor<KdCustomerMapping> captor = ArgumentCaptor.forClass(KdCustomerMapping.class);
        verify(mappingRepository).save(captor.capture());
        assertEquals(9L, captor.getValue().getOwnerUserId(), "业务员保存归属必须是本人，忽略入参");
        assertEquals(KdCustomerMapping.TYPE_MANUAL, captor.getValue().getMatchType());

        // 管理员不指定归属人必须拒绝：映射行不能没有归属
        when(operatorContext.current()).thenReturn(user("admin"));
        assertThrows(BusinessException.class,
                () -> service.save(saveReq("ACCT_A", 1001L, 5001L, null)));
        verify(mappingRepository, times(1)).save(any(KdCustomerMapping.class));
    }

    /* ---------------- 工具 ---------------- */

    private static <T> PageResult<T> pageOf(T... records) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(new ArrayList<>(List.of(records)));
        page.setTotal(records.length);
        page.setPageNum(1);
        page.setPageSize(10_000);
        page.setPages(1);
        return page;
    }

    private static LoginUser user(String roleKey) {
        LoginUser user = new LoginUser();
        user.setUserId(9L);
        user.setRoles(java.util.Set.of(roleKey));
        return user;
    }

    private static KdCustomer kd(Long fid, String acctId, String name) {
        KdCustomer customer = new KdCustomer();
        customer.setId(fid);
        customer.setKdFid(fid);
        customer.setAcctId(acctId);
        customer.setCustomerName(name);
        return customer;
    }

    private static Customer biz(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerName(name);
        return customer;
    }

    private static KdCustomerMapping mapping(Long kdFid, String acctId, Long bizId, String type) {
        KdCustomerMapping mapping = new KdCustomerMapping();
        mapping.setOwnerUserId(null);
        mapping.setAcctId(acctId);
        mapping.setKdFid(kdFid);
        mapping.setBizCustomerId(bizId);
        mapping.setMatchType(type);
        return mapping;
    }

    private static KdMappingSaveReq saveReq(String acctId, Long kdFid, Long bizId, String ownerUserId) {
        KdMappingSaveReq req = new KdMappingSaveReq();
        req.setAcctId(acctId);
        req.setKdFid(kdFid);
        req.setBizCustomerId(bizId);
        req.setOwnerUserId(ownerUserId);
        return req;
    }
}
