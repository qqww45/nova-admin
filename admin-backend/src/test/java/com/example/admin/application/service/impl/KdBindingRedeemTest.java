package com.example.admin.application.service.impl;

import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.model.kd.KdBindingCode;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.KdBindingCodeRepository;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdCustomerRepository;
import com.example.admin.domain.repository.KdOutboundRepository;
import com.example.admin.interfaces.dto.req.KdBindingRedeemReq;
import com.example.admin.interfaces.dto.resp.KdBindingRedeemResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 绑定兑换回归测试。
 *
 * <p>回归点：新建绑定分支原本丢弃了 {@code save} 的返回值，领域对象上的 id 仍为 null，
 * 兑换响应里 {@code bindingId} 就是 null。客户端用 {@code token && bindingId} 判断是否已绑定，
 * 于是"令牌拿到了却显示未绑定"，指令通道也不会启动。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@ExtendWith(MockitoExtension.class)
class KdBindingRedeemTest {

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
    @DisplayName("首次绑定（新建）时兑换响应必须带上 bindingId")
    void redeemShouldReturnBindingIdForNewBinding() {
        KdBindingCode code = new KdBindingCode();
        code.setId(1L);
        code.setUserId(9L);
        code.setExpireAt(LocalDateTime.now().plusMinutes(3));
        when(codeRepository.findByHash(anyString())).thenReturn(code);
        when(codeRepository.markUsed(anyLong(), any())).thenReturn(true);
        when(bindingRepository.findByUserAndAcct(anyLong(), anyString())).thenReturn(null);
        when(bindingRepository.save(any())).thenReturn(777L);

        KdBindingRedeemResp resp = bindingService.redeem(req());

        assertNotNull(resp.getToken(), "必须下发令牌");
        assertEquals("777", resp.getBindingId(), "新建绑定的 bindingId 不能为空");
        assertEquals("ACCT_X", resp.getAcctId());
    }

    @Test
    @DisplayName("重新绑定时沿用已有绑定的 bindingId")
    void redeemShouldReuseBindingIdForExistingBinding() {
        KdBindingCode code = new KdBindingCode();
        code.setId(2L);
        code.setUserId(9L);
        code.setExpireAt(LocalDateTime.now().plusMinutes(3));
        KdBinding existing = new KdBinding();
        existing.setId(555L);
        existing.setUserId(9L);
        existing.setAcctId("ACCT_X");
        when(codeRepository.findByHash(anyString())).thenReturn(code);
        when(codeRepository.markUsed(anyLong(), any())).thenReturn(true);
        when(bindingRepository.findByUserAndAcct(anyLong(), anyString())).thenReturn(existing);

        KdBindingRedeemResp resp = bindingService.redeem(req());

        assertEquals("555", resp.getBindingId());
    }

    private static KdBindingRedeemReq req() {
        KdBindingRedeemReq req = new KdBindingRedeemReq();
        req.setCode("ABCD2345");
        req.setAcctId("ACCT_X");
        req.setAcctName("测试账套");
        req.setMachineName("smoke-pc");
        req.setClientVersion("0.0.1");
        return req;
    }
}
