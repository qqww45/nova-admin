package com.example.admin.domain.model.kd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金蝶同步领域模型测试：水位、分录键、作废派生。
 *
 * <p>这三处都是"看起来只是个小函数"的地方，但错了会直接导致对账数据出错：
 * 水位回拨 → 重复拉取或漏单；分录键不稳 → 每次同步都新增一批重复分录；
 * 作废派生错 → 已作废的单据仍被算进应收。
 *
 * @author admin-system
 * @since 2026-11-02
 */
class KdSyncDomainTest {

    @Test
    @DisplayName("水位只进不退：乱序上报不会把水位回拨")
    void watermarkShouldNeverGoBackwards() {
        LocalDateTime later = LocalDateTime.of(2026, 11, 2, 12, 0);
        LocalDateTime earlier = LocalDateTime.of(2026, 11, 1, 12, 0);

        assertEquals(later, KdBinding.maxWatermark(later, earlier));
        assertEquals(later, KdBinding.maxWatermark(earlier, later));
        assertEquals(later, KdBinding.maxWatermark(null, later));
        assertEquals(later, KdBinding.maxWatermark(later, null));
        assertEquals(null, KdBinding.maxWatermark(null, null));
    }

    @Test
    @DisplayName("分录键：优先用金蝶内码，缺失时用单据+行序号生成稳定键")
    void entryIdShouldFallbackToStableKey() {
        assertEquals(9001L, KdOutboundLine.resolveEntryId(9001L, 123L, 3));
        long first = KdOutboundLine.resolveEntryId(null, 123L, 0);
        long again = KdOutboundLine.resolveEntryId(null, 123L, 0);
        long secondLine = KdOutboundLine.resolveEntryId(0L, 123L, 1);
        assertEquals(first, again, "同一分录重复上报必须得到同一个键");
        assertEquals(first + 1, secondLine);
        // 与其它单据的行不冲突
        assertEquals(first + 100_000L, KdOutboundLine.resolveEntryId(null, 124L, 0));
    }

    @Test
    @DisplayName("作废状态派生：只有 B 视为作废")
    void cancelledShouldOnlyBeTrueForB() {
        assertEquals(1, KdOutbound.cancelledOf("B"));
        assertEquals(1, KdOutbound.cancelledOf("b "));
        assertEquals(0, KdOutbound.cancelledOf("A"));
        assertEquals(0, KdOutbound.cancelledOf(""));
        assertEquals(0, KdOutbound.cancelledOf(null));
    }

    @Test
    @DisplayName("绑定码：未使用且在有效期内才可用")
    void bindingCodeUsability() {
        KdBindingCode code = new KdBindingCode();
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
        code.setExpireAt(now.plusMinutes(1));
        assertTrue(code.isUsable(now));

        code.setUsedAt(now);
        assertEquals(false, code.isUsable(now));

        code.setUsedAt(null);
        assertEquals(false, code.isUsable(now.plusMinutes(2)));
    }

    @Test
    @DisplayName("upsert 结果可累加，便于分批上报统计")
    void upsertResultShouldBeAdditive() {
        KdUpsertResult merged = new KdUpsertResult(10, 9, 1).plus(new KdUpsertResult(5, 5, 0));
        assertEquals(15, merged.received());
        assertEquals(14, merged.upserted());
        assertEquals(1, merged.skipped());
    }
}
