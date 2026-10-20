package com.example.admin.domain.model.imports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入批次测试：失败行明细的存 / 取。
 *
 * <p>《错误清单 xlsx》是事后下载，明细必须能在批次里完整还原，
 * 否则用户第二天下载到的就是一份空表。
 *
 * @author admin-system
 * @since 2026-01-01
 */
class ImportBatchTest {

    @Test
    @DisplayName("写入失败行后可原样取出：行号、原因、原始报文")
    void attachErrorsShouldRoundTrip() {
        ImportBatch batch = new ImportBatch();
        batch.attachErrors(List.of(
                new ImportErrorLine(3, "型号不能为空", "PO-1,,M-100"),
                new ImportErrorLine(7, "客户不存在：未知客户", "PO-2,未知客户,M-200")));

        List<ImportErrorLine> lines = batch.errors();
        assertEquals(2, lines.size());
        assertEquals(3, lines.get(0).lineNo());
        assertEquals("型号不能为空", lines.get(0).message());
        assertEquals("PO-1,,M-100", lines.get(0).raw());
        assertEquals(7, lines.get(1).lineNo());
    }

    @Test
    @DisplayName("没有失败行时不写入冗余 JSON")
    void emptyErrorsShouldBeNull() {
        ImportBatch batch = new ImportBatch();
        batch.attachErrors(List.of());
        assertNull(batch.getErrorDetail());
        assertTrue(batch.errors().isEmpty());
    }

    @Test
    @DisplayName("未写入过明细时取出空列表，而不是抛异常")
    void nullDetailShouldReturnEmpty() {
        assertTrue(new ImportBatch().errors().isEmpty());
    }

    @Test
    @DisplayName("原始报文含逗号与引号时仍能正确还原")
    void rawWithSpecialCharsShouldSurvive() {
        ImportBatch batch = new ImportBatch();
        batch.attachErrors(List.of(new ImportErrorLine(1, "数量格式不正确", "PO-3,\"A,B\",1.5x")));
        assertEquals("PO-3,\"A,B\",1.5x", batch.errors().get(0).raw());
    }
}
