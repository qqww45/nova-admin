package com.example.admin.application.service.impl;

import com.example.admin.domain.model.kd.KdClientIdentity;
import com.example.admin.domain.model.kd.KdFieldMapping;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.model.kd.KdSyncBatch;
import com.example.admin.domain.model.kd.KdUpsertResult;
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
import com.example.admin.interfaces.dto.req.KdOutboundLineSyncReq;
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

    @Test
    @DisplayName("分录上行应原样透传超长规格——300~1000 字不再整批拒绝，也不做截断")
    void uploadLinesShouldCarryLongSpecUntouched() {
        // 回归背景：2026-11-03 生产批次因 rows[472].spec 超 300 字被整批拒绝
        //（size must be between 0 and 300）。规格是对账依据，服务端不得截断。
        when(kdClientContext.require()).thenReturn(new KdClientIdentity(9L, 7L, "ACCT_A"));
        KdSyncBatch batch = new KdSyncBatch();
        batch.setId(1L);
        batch.setBatchNo("B1");
        batch.setBindingId(9L);
        batch.setRowCount(0);
        batch.setUpsertCount(0);
        batch.setSkippedCount(0);
        when(batchRepository.findByBatchNo("B1")).thenReturn(batch);
        when(lineRepository.upsertAll(any(), any(), any(), any()))
                .thenReturn(new KdUpsertResult(1, 1, 0));
        when(bindingRepository.findById(any())).thenReturn(null);

        String longSpec = "规".repeat(301);
        KdOutboundLineSyncReq req = new KdOutboundLineSyncReq();
        req.setBatchId("B1");
        KdOutboundLineSyncReq.Row row = new KdOutboundLineSyncReq.Row();
        row.setOutboundFid(1001L);
        row.setEntryId(100001L);
        row.setMaterialNumber("M001");
        row.setMaterialName("物料");
        row.setSpec(longSpec);
        req.setRows(List.of(row));

        var resp = service.uploadLines(req);

        assertEquals(1, resp.getUpserted());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KdOutboundLine>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(lineRepository).upsertAll(any(), any(), any(), captor.capture());
        List<KdOutboundLine> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(longSpec, saved.get(0).getSpec(), "规格必须原样落库，不得截断");
        assertEquals(301, saved.get(0).getSpec().length());
    }
}
