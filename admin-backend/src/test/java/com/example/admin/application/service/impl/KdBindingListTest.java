package com.example.admin.application.service.impl;

import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.common.IdName;
import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.KdBindingCodeRepository;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdCustomerRepository;
import com.example.admin.domain.repository.KdOutboundRepository;
import com.example.admin.interfaces.dto.resp.KdBindingResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 绑定列表水位 / 上次同步展示回归。
 *
 * <p>生产页「客户 / 出库」已是 525 / 30506，水位却显示「—」、上次同步显示「从未同步」。
 * 根因：采集器上报块不带 {@code watermarkTo}，finish 失败时也不写 {@code last_sync_at}，
 * 绑定行这两个字段为空。列表必须用已落库行的真实时间回填，不能编造时间。
 *
 * @author admin-system
 * @since 2026-11-04
 */
@ExtendWith(MockitoExtension.class)
class KdBindingListTest {

    @Mock
    private KdBindingRepository bindingRepository;

    @Mock
    private KdBindingCodeRepository codeRepository;

    @Mock
    private KdCustomerRepository customerRepository;

    @Mock
    private KdOutboundRepository outboundRepository;

    @Mock
    private BizUserRepository bizUserRepository;

    @Mock
    private OperatorContext operatorContext;

    @InjectMocks
    private KdBindingAppServiceImpl bindingService;

    @Test
    @DisplayName("绑定行水位/上次同步为空但已有落库数据时，用 sync_at / kd_modify_time 回填")
    void listShouldFillWatermarkAndLastSyncFromStoredRows() {
        when(operatorContext.current()).thenReturn(admin());
        KdBinding binding = binding(null, null, null);
        when(bindingRepository.listByUser(isNull())).thenReturn(List.of(binding));
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of(new IdName(9L, "yy")));
        when(customerRepository.countByOwner(9L, "ACCT_A")).thenReturn(525L);
        when(outboundRepository.countByOwner(9L, "ACCT_A")).thenReturn(30506L);
        LocalDateTime customerSync = LocalDateTime.of(2026, 11, 3, 10, 0, 0);
        LocalDateTime outboundSync = LocalDateTime.of(2026, 11, 3, 18, 30, 0);
        LocalDateTime outboundModify = LocalDateTime.of(2026, 11, 2, 15, 0, 0);
        when(customerRepository.maxSyncAt(9L, "ACCT_A")).thenReturn(customerSync);
        when(outboundRepository.maxSyncAt(9L, "ACCT_A")).thenReturn(outboundSync);
        when(outboundRepository.maxModifyTime(9L, "ACCT_A")).thenReturn(outboundModify);

        List<KdBindingResp> list = bindingService.list();

        assertEquals(1, list.size());
        KdBindingResp row = list.get(0);
        assertEquals(525L, row.getCustomerCount());
        assertEquals(30506L, row.getOutboundCount());
        assertEquals(customerSync, row.getWatermarkCustomer(), "客户水位应取客户行最大 sync_at");
        assertEquals(outboundModify, row.getWatermarkOutbound(), "出库水位优先取金蝶修改时间");
        assertEquals(outboundSync, row.getLastSyncAt(), "上次同步应取客户/出库 sync_at 的较新值");
    }

    @Test
    @DisplayName("出库没有 FModifyDate 时，出库水位退回出库 sync_at")
    void listShouldFallBackOutboundWatermarkToSyncAt() {
        when(operatorContext.current()).thenReturn(admin());
        when(bindingRepository.listByUser(isNull())).thenReturn(List.of(binding(null, null, null)));
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of(new IdName(9L, "yy")));
        when(customerRepository.countByOwner(9L, "ACCT_A")).thenReturn(1L);
        when(outboundRepository.countByOwner(9L, "ACCT_A")).thenReturn(2L);
        LocalDateTime outboundSync = LocalDateTime.of(2026, 11, 3, 12, 0, 0);
        when(customerRepository.maxSyncAt(eq(9L), eq("ACCT_A"))).thenReturn(null);
        when(outboundRepository.maxSyncAt(9L, "ACCT_A")).thenReturn(outboundSync);
        when(outboundRepository.maxModifyTime(9L, "ACCT_A")).thenReturn(null);

        KdBindingResp row = bindingService.list().get(0);

        assertEquals(outboundSync, row.getWatermarkOutbound());
        assertEquals(outboundSync, row.getLastSyncAt());
        assertNull(row.getWatermarkCustomer(), "没有客户数据时客户水位保持空，页面仍显示 —");
    }

    @Test
    @DisplayName("绑定行已有水位和上次同步时，不覆盖、也不多查兜底")
    void listShouldKeepStoredWatermarkAndLastSync() {
        LocalDateTime storedCustomer = LocalDateTime.of(2026, 10, 1, 8, 0, 0);
        LocalDateTime storedOutbound = LocalDateTime.of(2026, 10, 1, 9, 0, 0);
        LocalDateTime storedSync = LocalDateTime.of(2026, 10, 1, 10, 0, 0);
        when(operatorContext.current()).thenReturn(admin());
        when(bindingRepository.listByUser(isNull()))
                .thenReturn(List.of(binding(storedCustomer, storedOutbound, storedSync)));
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of(new IdName(9L, "yy")));
        when(customerRepository.countByOwner(9L, "ACCT_A")).thenReturn(10L);
        when(outboundRepository.countByOwner(9L, "ACCT_A")).thenReturn(20L);

        KdBindingResp row = bindingService.list().get(0);

        assertEquals(storedCustomer, row.getWatermarkCustomer());
        assertEquals(storedOutbound, row.getWatermarkOutbound());
        assertEquals(storedSync, row.getLastSyncAt());
        verify(customerRepository, never()).maxSyncAt(eq(9L), eq("ACCT_A"));
        verify(outboundRepository, never()).maxSyncAt(eq(9L), eq("ACCT_A"));
        verify(outboundRepository, never()).maxModifyTime(eq(9L), eq("ACCT_A"));
    }

    @Test
    @DisplayName("没有落库数据时水位和上次同步保持空，页面显示 — / 从未同步")
    void listShouldKeepEmptyWhenNoStoredRows() {
        when(operatorContext.current()).thenReturn(admin());
        when(bindingRepository.listByUser(isNull())).thenReturn(List.of(binding(null, null, null)));
        when(bizUserRepository.findNames(anyList())).thenReturn(List.of(new IdName(9L, "yy")));
        when(customerRepository.countByOwner(9L, "ACCT_A")).thenReturn(0L);
        when(outboundRepository.countByOwner(9L, "ACCT_A")).thenReturn(0L);
        when(customerRepository.maxSyncAt(9L, "ACCT_A")).thenReturn(null);
        when(outboundRepository.maxSyncAt(9L, "ACCT_A")).thenReturn(null);
        when(outboundRepository.maxModifyTime(9L, "ACCT_A")).thenReturn(null);

        KdBindingResp row = bindingService.list().get(0);

        assertNull(row.getWatermarkCustomer());
        assertNull(row.getWatermarkOutbound());
        assertNull(row.getLastSyncAt());
    }

    private static LoginUser admin() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setRoles(java.util.Set.of("admin"));
        return user;
    }

    private static KdBinding binding(
            LocalDateTime watermarkCustomer, LocalDateTime watermarkOutbound, LocalDateTime lastSyncAt) {
        KdBinding binding = new KdBinding();
        binding.setId(100L);
        binding.setUserId(9L);
        binding.setAcctId("ACCT_A");
        binding.setAcctName("深圳市一恒科电子科技有限公司");
        binding.setStatus("ACTIVE");
        binding.setWatermarkCustomer(watermarkCustomer);
        binding.setWatermarkOutbound(watermarkOutbound);
        binding.setLastSyncAt(lastSyncAt);
        return binding;
    }
}
