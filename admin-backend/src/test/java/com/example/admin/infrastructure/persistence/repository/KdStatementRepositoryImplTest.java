package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundLineMapper;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundMapper;
import com.example.admin.infrastructure.persistence.po.KdOutboundLinePo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账取数仓储回归测试。
 *
 * <p>回归点：金蝶 FID 只在账套内唯一。曾经只按 {@code outbound_fid} 查分录，
 * 导致 A、B 两个账套都有 {@code fid=90001} 时，A 的分录挂到 B 的单上
 * （页面表现为"一张单 4 行、金额翻倍"）。这里锁住"必须按账套分组查询"。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@ExtendWith(MockitoExtension.class)
class KdStatementRepositoryImplTest {

    @Mock
    private KdOutboundMapper outboundMapper;

    @Mock
    private KdOutboundLineMapper lineMapper;

    @Mock
    private KdConverter converter;

    @InjectMocks
    private KdStatementRepositoryImpl repository;

    /**
     * Lambda 条件依赖 TableInfo 缓存解析列名；脱离 Spring 时需手工初始化一次，
     * 否则 {@code .in(...)} 会抛 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), KdOutboundLinePo.class);
    }

    @Test
    @DisplayName("不同账套存在相同 FID 时，分录查询必须按账套分别过滤")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listLinesShouldScopeByAcct() {
        when(lineMapper.selectList(any())).thenReturn(List.of());

        repository.listLines(null, List.of(bill("ACCT_A", 90001L), bill("ACCT_B", 90001L)));

        ArgumentCaptor<LambdaQueryWrapper<KdOutboundLinePo>> captor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(lineMapper, times(2)).selectList(captor.capture());

        List<LambdaQueryWrapper<KdOutboundLinePo>> wrappers = captor.getAllValues();
        assertEquals(2, wrappers.size(), "两个账套应各查一次");
        for (LambdaQueryWrapper<KdOutboundLinePo> wrapper : wrappers) {
            assertTrue(wrapper.getSqlSegment().contains("acct_id"),
                    "分录查询缺少账套条件会串账套：" + wrapper.getSqlSegment());
        }
        List<Object> params = wrappers.stream()
                .flatMap(w -> w.getParamNameValuePairs().values().stream())
                .toList();
        assertTrue(params.contains("ACCT_A") && params.contains("ACCT_B"),
                "两个账套都应作为条件值出现：" + params);
    }

    @Test
    @DisplayName("同一账套多张单只查一次，不因分组退化成 N 次查询")
    void listLinesShouldBatchWithinSameAcct() {
        when(lineMapper.selectList(any())).thenReturn(List.of());

        repository.listLines(null, List.of(bill("ACCT_A", 1L), bill("ACCT_A", 2L), bill("ACCT_A", 3L)));

        verify(lineMapper, times(1)).selectList(any());
    }

    private static KdOutbound bill(String acctId, Long fid) {
        KdOutbound bill = new KdOutbound();
        bill.setAcctId(acctId);
        bill.setKdFid(fid);
        return bill;
    }
}
