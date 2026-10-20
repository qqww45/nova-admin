package com.example.admin.domain.service;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.model.mapping.ImportMapping;
import com.example.admin.domain.model.shipment.Shipment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 导入相关单元测试：表头模糊匹配、值解析、出货幂等键（附录A A.1 / A.4）。 */
class ImportParseTest {

    private static ImportMapping mapping(String field, String aliases) {
        ImportMapping mapping = new ImportMapping();
        mapping.setFieldName(field);
        mapping.setAliases(aliases);
        mapping.setRequired(0);
        return mapping;
    }

    /* ---------------- 表头归一化与匹配 ---------------- */

    @Test
    @DisplayName("归一化：去空格、去换行、全角转半角、忽略括号内容")
    void normalize_stripsNoise() {
        assertThat(HeaderNormalizer.normalize(" 客户 名称 ")).isEqualTo("客户名称");
        assertThat(HeaderNormalizer.normalize("客户名称\n")).isEqualTo("客户名称");
        assertThat(HeaderNormalizer.normalize("Ｍｏｄｅｌ")).isEqualTo("model");
        assertThat(HeaderNormalizer.normalize("备注(订单)")).isEqualTo("备注订单");
        assertThat(HeaderNormalizer.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("中英文别名都能匹配到同一个系统字段")
    void matchField_aliasInBothLanguages() {
        List<ImportMapping> mappings = List.of(mapping("customer_name", "客户名称,客户名,Customer,Client"));
        assertThat(HeaderNormalizer.matchField("客户名称", mappings)).isEqualTo("customer_name");
        assertThat(HeaderNormalizer.matchField("客户名", mappings)).isEqualTo("customer_name");
        assertThat(HeaderNormalizer.matchField("Customer", mappings)).isEqualTo("customer_name");
        assertThat(HeaderNormalizer.matchField(" CLIENT ", mappings)).isEqualTo("customer_name");
    }

    @Test
    @DisplayName("「备注」与「备注(订单)」必须区分开（括号归一化后精确命中订单备注）")
    void matchField_remarkVsOrderRemark() {
        List<ImportMapping> mappings = List.of(
                mapping("remark", "备注,Remark"),
                mapping("order_remark", "备注订单,订单备注,Order Remark"));
        assertThat(HeaderNormalizer.matchField("备注", mappings)).isEqualTo("remark");
        assertThat(HeaderNormalizer.matchField("备注(订单)", mappings)).isEqualTo("order_remark");
        assertThat(HeaderNormalizer.matchField("订单备注", mappings)).isEqualTo("order_remark");
    }

    @Test
    @DisplayName("未识别的表头返回 null（由调用方计入警告而非崩溃）")
    void matchField_unknownReturnsNull() {
        List<ImportMapping> mappings = List.of(mapping("model", "型号,Model"));
        assertThat(HeaderNormalizer.matchField("莫名其妙的列", mappings)).isNull();
        assertThat(HeaderNormalizer.matchField(null, mappings)).isNull();
    }

    /* ---------------- 值解析 ---------------- */

    @Test
    @DisplayName("数值：支持千分位、全角与单位后缀")
    void toNumber_tolerant() {
        assertThat(ValueParser.toNumber("1,234.5", "数量")).isEqualByComparingTo("1234.5");
        assertThat(ValueParser.toNumber(" 100 ", "数量")).isEqualByComparingTo("100");
        assertThat(ValueParser.toNumber("１００", "数量")).isEqualByComparingTo("100");
        assertThat(ValueParser.toNumber("100 pcs", "数量")).isEqualByComparingTo("100");
        assertThat(ValueParser.toNumber("", "数量")).isNull();
    }

    @Test
    @DisplayName("数值非法：抛业务异常且消息可直接进错误清单")
    void toNumber_invalidThrows() {
        assertThatThrownBy(() -> ValueParser.toNumber("abc", "数量"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量");
    }

    @Test
    @DisplayName("税率：13% / 13 / 0.13 都归一为 0.13；空为 null")
    void toRate_variousForms() {
        assertThat(ValueParser.toRate("13%", "税率")).isEqualByComparingTo("0.1300");
        assertThat(ValueParser.toRate("13", "税率")).isEqualByComparingTo("0.1300");
        assertThat(ValueParser.toRate("0.13", "税率")).isEqualByComparingTo("0.1300");
        assertThat(ValueParser.toRate("", "税率")).isNull();
        assertThat(ValueParser.toRate("0", "税率")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("日期：yyyy-MM-dd / yyyy/M/d / Excel 序列 都能解析")
    void toDate_variousForms() {
        assertThat(ValueParser.toDate("2026-01-05", "出货日期").toLocalDate().toString()).isEqualTo("2026-01-05");
        assertThat(ValueParser.toDate("2026/1/5", "出货日期").toLocalDate().toString()).isEqualTo("2026-01-05");
        // Excel 序列 45000 → 2023-03-15
        assertThat(ValueParser.toDate("45000", "出货日期").toLocalDate().toString()).isEqualTo("2023-03-15");
        assertThat(ValueParser.toDate("", "出货日期")).isNull();
    }

    @Test
    @DisplayName("日期非法：抛业务异常")
    void toDate_invalidThrows() {
        assertThatThrownBy(() -> ValueParser.toDate("不是日期", "出货日期"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出货日期");
    }

    /* ---------------- 出货幂等键 ---------------- */

    @Test
    @DisplayName("幂等键：加工单号 + 客户订单号 + 料号 + 日期 + 数量 完全一致才判重")
    void idempotentKey_sameContentSameKey() {
        LocalDateTime date = LocalDateTime.of(2026, 1, 5, 0, 0);
        String key1 = Shipment.idempotentKey("JG-001", "PO-1", "M-1", date, new BigDecimal("10"));
        String key2 = Shipment.idempotentKey("JG-001", "PO-1", "M-1", date, new BigDecimal("10.0"));
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    @DisplayName("幂等键：任一项不同即为不同记录（重复导入不会误杀正常数据）")
    void idempotentKey_anyDifferenceDiffers() {
        LocalDateTime date = LocalDateTime.of(2026, 1, 5, 0, 0);
        String base = Shipment.idempotentKey("JG-001", "PO-1", "M-1", date, new BigDecimal("10"));
        assertThat(Shipment.idempotentKey("JG-002", "PO-1", "M-1", date, new BigDecimal("10"))).isNotEqualTo(base);
        assertThat(Shipment.idempotentKey("JG-001", "PO-2", "M-1", date, new BigDecimal("10"))).isNotEqualTo(base);
        assertThat(Shipment.idempotentKey("JG-001", "PO-1", "M-2", date, new BigDecimal("10"))).isNotEqualTo(base);
        assertThat(Shipment.idempotentKey("JG-001", "PO-1", "M-1", date.plusDays(1), new BigDecimal("10")))
                .isNotEqualTo(base);
        assertThat(Shipment.idempotentKey("JG-001", "PO-1", "M-1", date, new BigDecimal("11"))).isNotEqualTo(base);
    }

    @Test
    @DisplayName("幂等键：字段为空时也要稳定（避免 null 参与拼接导致 NPE）")
    void idempotentKey_nullSafe() {
        LocalDateTime date = LocalDateTime.of(2026, 1, 5, 0, 0);
        assertThat(Shipment.idempotentKey(null, null, null, date, new BigDecimal("10")))
                .isEqualTo("|||2026-01-05|10");
        assertThat(Shipment.idempotentKey("JG-001", "PO-1", "M-1", null, null))
                .isEqualTo("JG-001|PO-1|M-1||");
    }
}
