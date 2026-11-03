package com.example.admin.application.service.impl;

import com.example.admin.domain.model.kd.KdClientIdentity;
import com.example.admin.domain.model.kd.KdFieldMapping;
import com.example.admin.domain.port.KdClientContext;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdCustomerRepository;
import com.example.admin.domain.repository.KdFieldMappingRepository;
import com.example.admin.domain.repository.KdOutboundLineRepository;
import com.example.admin.domain.repository.KdOutboundRepository;
import com.example.admin.domain.repository.KdSyncBatchRepository;
import com.example.admin.domain.repository.KdSyncTaskRepository;
import com.example.admin.interfaces.dto.req.KdMappingUploadReq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采集器字段自检结果回传测试。
 *
 * <p>回归点：
 * <ul>
 *     <li>写到令牌对应的绑定（采集器只能写自己的绑定）；</li>
 *     <li>key 与管理端配置页约定一致（line_field_keys / customer_po_field）；</li>
 *     <li>空值跳过：只回传了部分字段时不覆盖其余键；已存在则更新不重复插入。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
class KdSyncAppServiceImplTest {

    @Mock
    private KdBindingRepository bindingRepository;

    @Mock
    private KdSyncTaskRepository taskRepository;

    @Mock
    private KdFieldMappingRepository mappingRepository;

    @Mock
    private KdCustomerRepository customerRepository;

    @Mock
    private KdOutboundRepository outboundRepository;

    @Mock
    private KdOutboundLineRepository lineRepository;

    @Mock
    private KdSyncBatchRepository batchRepository;

    @Mock
    private BizUserRepository bizUserRepository;

    @Mock
    private OperatorContext operatorContext;

    @Mock
    private KdClientContext kdClientContext;

    @InjectMocks
    private KdSyncAppServiceImpl service;

    @Test
    @DisplayName("回传应写到本绑定的 line_field_keys / customer_po_field，新键新增")
    void uploadMappingShouldSaveUnderAgreedKeys() {
        when(kdClientContext.require()).thenReturn(new KdClientIdentity(77L, 9L, "ACCT_A"));
        when(mappingRepository.find(any(), any())).thenReturn(null);

        KdMappingUploadReq req = new KdMappingUploadReq();
        req.setLineFieldKeys("FID,FBillNo,FEntity_FEntryID,FRealQty");
        req.setCustomerPoField("F_XBDR_PONo");
        service.uploadMapping(req);

        ArgumentCaptor<KdFieldMapping> captor = ArgumentCaptor.forClass(KdFieldMapping.class);
        verify(mappingRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<KdFieldMapping> saved = captor.getAllValues();
        assertEquals(77L, saved.get(0).getBindingId(), "必须写到令牌对应的绑定");
        assertEquals(KdSyncAppServiceImpl.MAPPING_KEY_LINE_FIELDS, saved.get(0).getMappingKey());
        assertEquals("FID,FBillNo,FEntity_FEntryID,FRealQty", saved.get(0).getMappingValue());
        assertEquals(KdSyncAppServiceImpl.MAPPING_KEY_CUSTOMER_PO, saved.get(1).getMappingKey());
        assertEquals("F_XBDR_PONo", saved.get(1).getMappingValue());
    }

    @Test
    @DisplayName("已存在的键应更新而不是重复插入；空值跳过不覆盖")
    void uploadMappingShouldUpdateExistingAndSkipBlank() {
        when(kdClientContext.require()).thenReturn(new KdClientIdentity(77L, 9L, "ACCT_A"));
        KdFieldMapping existing = new KdFieldMapping();
        existing.setBindingId(77L);
        existing.setMappingKey("line_field_keys");
        existing.setMappingValue("旧字段集");
        when(mappingRepository.find(77L, KdSyncAppServiceImpl.MAPPING_KEY_LINE_FIELDS)).thenReturn(existing);

        KdMappingUploadReq req = new KdMappingUploadReq();
        req.setLineFieldKeys("新字段集");
        // customerPoField 留空：不应触碰该键
        service.uploadMapping(req);

        verify(mappingRepository).update(any(KdFieldMapping.class));
        verify(mappingRepository, never()).save(any());
        ArgumentCaptor<KdFieldMapping> captor = ArgumentCaptor.forClass(KdFieldMapping.class);
        verify(mappingRepository).update(captor.capture());
        assertEquals("新字段集", captor.getValue().getMappingValue());
        verify(mappingRepository, never()).find(77L, KdSyncAppServiceImpl.MAPPING_KEY_CUSTOMER_PO);
    }

    @Test
    @DisplayName("全空回传应直接不落库（自检失败时的空报告不算有效结果）")
    void uploadMappingShouldDoNothingWhenAllBlank() {
        when(kdClientContext.require()).thenReturn(new KdClientIdentity(77L, 9L, "ACCT_A"));

        service.uploadMapping(new KdMappingUploadReq());

        verify(mappingRepository, never()).save(any());
        verify(mappingRepository, never()).update(any());
    }
}
