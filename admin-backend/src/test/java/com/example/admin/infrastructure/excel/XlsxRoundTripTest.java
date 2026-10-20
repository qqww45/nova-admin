package com.example.admin.infrastructure.excel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * xlsx 读写自测（手写实现，必须有往返测试兜底）。
 *
 * <p>覆盖：多页签、表头、数字与文本、中文、含特殊字符的文本、空行跳过、原始行号。
 */
class XlsxRoundTripTest {

    @Test
    @DisplayName("写入后读回：表头与数据行完全一致（含中文与数字）")
    void writeThenRead_contentPreserved() throws Exception {
        List<String> header = List.of("出货日期", "加工单号", "客户", "型号", "出货数量", "备注");
        List<List<String>> rows = List.of(
                List.of("2026-01-05", "JG-001", "华为技术有限公司", "ABC-100", "10", ""),
                List.of("2026-01-06", "JG-002", "小米科技", "XYZ-200", "3.5", "加急"));

        byte[] bytes = XlsxWriter.write(List.of(XlsxWriter.sheet("数据", header, rows)));
        List<XlsxReader.Row> read = XlsxReader.read(bytes, "数据");

        assertThat(read).hasSize(3);
        assertThat(read.get(0).cells()).containsExactly("出货日期", "加工单号", "客户", "型号", "出货数量", "备注");
        // 数据行的空"备注"不写成单元格，读回时该列补空串
        assertThat(read.get(1).cells()).containsExactly("2026-01-05", "JG-001", "华为技术有限公司", "ABC-100", "10", "");
        assertThat(read.get(2).cells()).containsExactly("2026-01-06", "JG-002", "小米科技", "XYZ-200", "3.5", "加急");
    }

    @Test
    @DisplayName("多个页签：可按表名分别读取")
    void writeThenRead_multipleSheets() throws Exception {
        byte[] bytes = XlsxWriter.write(List.of(
                XlsxWriter.sheet("数据", List.of("客户名称"), List.of(List.of("华为"))),
                XlsxWriter.sheet("字段说明", List.of("字段", "说明"), List.of(List.of("客户名称", "唯一键"))),
                XlsxWriter.sheet("税率字典", List.of("值", "标签"), List.of(List.of("0.13", "13%")))));

        assertThat(XlsxReader.sheetNames(bytes)).containsExactly("数据", "字段说明", "税率字典");
        assertThat(XlsxReader.read(bytes, "税率字典").get(0).cells()).containsExactly("值", "标签");
        assertThat(XlsxReader.read(bytes, "税率字典").get(1).cells()).containsExactly("0.13", "13%");
        // 不指定表名时读第一个
        assertThat(XlsxReader.read(bytes).get(1).cells()).containsExactly("华为");
    }

    @Test
    @DisplayName("特殊字符：& < > 引号 不破坏 XML 结构")
    void writeThenRead_specialCharsEscaped() throws Exception {
        List<String> header = List.of("客户", "要求");
        List<List<String>> rows = List.of(List.of("A&B 电子 <深圳>", "包装需防潮 \"双层\""));

        byte[] bytes = XlsxWriter.write(List.of(XlsxWriter.sheet("数据", header, rows)));
        List<XlsxReader.Row> read = XlsxReader.read(bytes, "数据");

        assertThat(read.get(1).cells()).containsExactly("A&B 电子 <深圳>", "包装需防潮 \"双层\"");
    }

    @Test
    @DisplayName("原始行号：空行被跳过但行号仍与 Excel 一致")
    void writeThenRead_rowNumbersKept() throws Exception {
        List<String> header = List.of("型号", "数量");
        List<List<String>> rows = List.of(
                List.of("A-1", "1"),
                List.of("", ""),
                List.of("A-2", "2"));

        byte[] bytes = XlsxWriter.write(List.of(XlsxWriter.sheet("数据", header, rows)));
        List<XlsxReader.Row> read = XlsxReader.read(bytes, "数据");

        assertThat(read).hasSize(3);
        assertThat(read.get(2).rowNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("前导零：料号 0012 不被写成数字 12")
    void writeThenRead_leadingZeroKept() throws Exception {
        List<String> header = List.of("料号");
        List<List<String>> rows = List.of(List.of("0012"), List.of("12"));

        byte[] bytes = XlsxWriter.write(List.of(XlsxWriter.sheet("数据", header, rows)));
        List<XlsxReader.Row> read = XlsxReader.read(bytes, "数据");

        assertThat(read.get(1).cells()).containsExactly("0012");
        assertThat(read.get(2).cells()).containsExactly("12");
    }

    @Test
    @DisplayName("表名清洗：非法字符与超长表名被处理，仍能正常读回")
    void writeThenRead_sheetNameSanitized() throws Exception {
        byte[] bytes = XlsxWriter.write(List.of(
                XlsxWriter.sheet("这是一个非常非常非常非常非常非常非常长的表名需要被截断", List.of("A"), List.of(List.of("1")))));

        List<String> names = XlsxReader.sheetNames(bytes);
        assertThat(names).hasSize(1);
        assertThat(names.get(0).length()).isLessThanOrEqualTo(31);
        assertThat(XlsxReader.read(bytes, names.get(0)).get(1).cells()).containsExactly("1");
    }
}
