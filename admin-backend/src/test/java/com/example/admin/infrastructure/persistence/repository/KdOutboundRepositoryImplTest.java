package com.example.admin.infrastructure.persistence.repository;

import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundMapper;
import com.example.admin.infrastructure.persistence.po.KdOutboundPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出库单头仓储：金额回填必须走一条 set-based UPDATE。
 *
 * @author admin-system
 * @since 2026-11-14
 */
@ExtendWith(MockitoExtension.class)
class KdOutboundRepositoryImplTest {

    @Mock
    private KdOutboundMapper outboundMapper;

    @Mock
    private KdConverter converter;

    @InjectMocks
    private KdOutboundRepositoryImpl repository;

    @Test
    @DisplayName("recalculateAmountByBatch 只发一次 set-based UPDATE，不再按单头循环 update")
    void recalculateAmountByBatchShouldIssueSingleSetBasedUpdate() {
        when(outboundMapper.updateAmountFromLinesByBatch(7L, "ACCT_A", "B-FIN")).thenReturn(12);

        int updated = repository.recalculateAmountByBatch(7L, "ACCT_A", "B-FIN");

        assertEquals(12, updated);
        verify(outboundMapper).updateAmountFromLinesByBatch(7L, "ACCT_A", "B-FIN");
        verify(outboundMapper, never()).update(any(), any());
        verify(outboundMapper, never()).updateById(any(KdOutboundPo.class));
    }
}
