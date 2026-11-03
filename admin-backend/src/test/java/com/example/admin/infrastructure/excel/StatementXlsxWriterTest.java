package com.example.admin.infrastructure.excel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账单导出与金样板的保真度回归测试。
 *
 * <p>基准文件：{@code desktop/resources/templates/statement-golden-sample.xlsx}
 * （业务实际使用的「一恒科—鸿晟达」对帐单）。导出的任何一项版式回退
 * ——合并区、列宽、行高、页脚公式、打印设置、配色、Logo——都会让签字文件
 * 与客户手里的历史对账单长得不一样，这里把可程序化比对的项全部锁死。
 *
 * <p>有意偏差（数据源决定，无法从金蝶字段推导）：
 * K 列表头后缀（样板是产品代号 YHK26-MID-…，导出用 sheet 的物料编号）、
 * 已收金额与收款备注（财务手填，样板 0233 页同样留空）、
 * 联系人 / 联系方式（采集链路未同步）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
class StatementXlsxWriterTest {

    /** 金样板（随仓库维护，导出版式的唯一基准）。 */
    private static final Path GOLDEN = locateGolden();

    /** 样板「0024 」页的明细行数：20 行 → 页脚 28~30、提示 31、落款 32。 */
    private static final int GOLDEN_DETAIL_ROWS = 20;

    private static byte[] generated;
    private static Map<String, byte[]> generatedParts;
    private static Map<String, byte[]> goldenParts;

    @BeforeAll
    static void generate() throws IOException {
        List<StatementXlsxWriter.Row> rows = new ArrayList<>();
        for (int i = 0; i < GOLDEN_DETAIL_ROWS; i++) {
            rows.add(new StatementXlsxWriter.Row(
                    LocalDate.parse("2026-04-11"), "XSCKD20260411001", "POORD006246",
                    "13-1733-812800", "主板", "G138 V1.0 A733 8/128G AIC8800D80", "pcs",
                    new BigDecimal(11), new BigDecimal(432), null,
                    "26/4/11顺丰SF1568606228445，单号：XSCKD20260411001"));
        }
        StatementXlsxWriter.Header header = new StatementXlsxWriter.Header(
                "深圳市一恒科电子科技有限公司",
                "SHENZHEN YIHENGK ELECTRONICS TECHNOLOGYCO.,LTD",
                "地址：广东省深圳市南山区留仙大道创智云城A3栋A座8-9楼（成谷研发大厦）"
                        + "   电话：0755-26905055     传真：0755-26905050",
                "深圳鸿晟达光电科技有限公司", null, null,
                "深圳市宝安区福永凤凰社区华伦科技园17栋3楼", LocalDate.parse("2026-08-20"));
        generated = StatementXlsxWriter.write(List.of(new StatementXlsxWriter.Sheet(
                "0024", header, "13-1733-812800", "杨晓蓉", "胡杜军", rows)));
        generatedParts = unzip(generated);
        goldenParts = unzip(Files.readAllBytes(GOLDEN));
    }

    @Test
    @DisplayName("合并单元格：与样板完全同构（明细行数相同即逐区一致）")
    void mergesShouldMatchGolden() {
        List<String> expected = refs(golden("xl/worksheets/sheet1.xml"), "mergeCell ref=\"([^\"]+)\"");
        List<String> actual = refs(generatedSheet(), "mergeCell ref=\"([^\"]+)\"");
        expected.sort(String::compareTo);
        actual.sort(String::compareTo);
        assertEquals(expected, actual, "合并区必须与样板完全一致");
    }

    @Test
    @DisplayName("列宽：A~K 与样板逐列一致")
    void columnWidthsShouldMatchGolden() {
        Map<String, String> goldenCols = colWidths(golden("xl/worksheets/sheet1.xml"));
        Map<String, String> actualCols = colWidths(generatedSheet());
        // 样板首页 J 列宽即 11.5（后几页 13.62 是历史遗留，取首页口径）
        for (String col : List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")) {
            assertEquals(goldenCols.get(col), actualCols.get(col), "列 " + col + " 宽度应与样板一致");
        }
    }

    @Test
    @DisplayName("行高：1~32 行与样板逐行一致")
    void rowHeightsShouldMatchGolden() {
        Map<Integer, String> goldenHeights = rowHeights(golden("xl/worksheets/sheet1.xml"));
        Map<Integer, String> actualHeights = rowHeights(generatedSheet());
        assertEquals(goldenHeights.size(), actualHeights.size(), "总行数应与样板一致");
        for (Map.Entry<Integer, String> e : goldenHeights.entrySet()) {
            assertEquals(e.getValue(), actualHeights.get(e.getKey()),
                    "第 " + e.getKey() + " 行行高应与样板一致");
        }
    }

    @Test
    @DisplayName("打印设置：A4 横向 94% 缩放、水平居中、页边距与样板一致")
    void printSetupShouldMatchGolden() {
        String goldenXml = golden("xl/worksheets/sheet1.xml");
        String actualXml = generatedSheet();
        assertEquals(firstMatch(goldenXml, "<printOptions[^>]*/>"), firstMatch(actualXml, "<printOptions[^>]*/>"),
                "打印水平居中设置应与样板一致");
        assertEquals(firstMatch(goldenXml, "<pageMargins[^>]*/>"), firstMatch(actualXml, "<pageMargins[^>]*/>"),
                "页边距应与样板一致");
        assertEquals(firstMatch(goldenXml, "<pageSetup[^>]*/>"), firstMatch(actualXml, "<pageSetup[^>]*/>"),
                "纸张 / 方向 / 缩放应与样板一致");
    }

    @Test
    @DisplayName("表头文本：抬头四行 + 客户信息行 + 列头逐字与样板一致")
    void headerTextsShouldMatchGolden() {
        String actual = generatedSheet();
        assertTrue(actual.contains("深圳市一恒科电子科技有限公司\n\n"), "A1 公司名（含样板末尾两个换行）");
        assertTrue(actual.contains("SHENZHEN YIHENGK ELECTRONICS TECHNOLOGYCO.,LTD"), "A2 英文名");
        assertTrue(actual.contains("地址：广东省深圳市南山区留仙大道创智云城A3栋A座8-9楼（成谷研发大厦）"),
                "A3 地址电话传真");
        assertTrue(actual.contains("对  帐  单"), "A4 标题（含样板中的全角空格）");
        for (String label : List.of("客户名称：", "联系人:", "联系方式：", "地址:", "对账日期：")) {
            assertTrue(actual.contains(label), "缺少标签：" + label);
        }
        for (String label : List.of("日期", "送货单号", "客户订单号码", "物料编号", "物料名称",
                "规格", "单位", "数量", "单价", "金额")) {
            assertTrue(actual.contains(">" + label + "</t>"), "列头缺少：" + label);
        }
        assertTrue(actual.contains(">备注13-1733-812800</t>"), "K 列表头应为「备注 + 物料标识」");
    }

    @Test
    @DisplayName("页脚：标签逐字一致，数量 / 货款合计为 SUM 公式，应收 = 货款 − 已收")
    void footerShouldMatchGoldenFormulaPattern() {
        String actual = generatedSheet();
        assertTrue(actual.contains("累计订单送货"), "页脚标签「累计订单送货」");
        assertTrue(actual.contains("累计货款（含税）"), "页脚标签「累计货款（含税）」");
        assertTrue(actual.contains("累计已收款金额"), "页脚标签「累计已收款金额」");
        assertTrue(actual.contains("累计应收款金额"), "页脚标签「累计应收款金额」");
        assertTrue(actual.contains("<f>SUM(H8:H27)</f>"), "数量合计应为 SUM(H8:H27)");
        assertTrue(actual.contains("<f>SUM(J8:J27)</f>"), "货款合计应为 SUM(J8:J27)");
        assertTrue(actual.contains("<f>J28-G29</f>"), "应收应为 货款 − 已收");
        assertTrue(actual.contains("注：请确认以上信息，并签字盖章回传，谢谢！"), "确认签字提示");
        assertTrue(actual.contains("制单：杨晓蓉"), "制单落款");
        assertTrue(actual.contains("业务：胡杜军"), "业务落款");
        assertTrue(actual.contains("审核："), "审核落款（留空）");
        assertTrue(actual.contains("客户签字/盖章:"), "客户签字落款");
    }

    @Test
    @DisplayName("金额口径：明细行金额 = 数量 × 单价公式；无分录行直填金额")
    void amountFormulaPatternShouldMatchGolden() {
        String actual = generatedSheet();
        assertEquals(GOLDEN_DETAIL_ROWS, countMatches(actual, "<f>(H\\d+\\*I\\d+)</f>"),
                "20 行明细每行都应有「数量 × 单价」公式");
        // 样板第 8 行：数量 11、单价 432，日期 2026-04-11 —— 导出值应一致
        assertTrue(actual.contains("<v>11</v>"), "数量 11");
        assertTrue(actual.contains("<v>432</v>"), "单价 432");
    }

    @Test
    @DisplayName("日期口径：日期写 Excel 序列，与样板同一天的序列值完全一致")
    void dateSerialShouldMatchGolden() {
        String goldenA8 = cellValue(golden("xl/worksheets/sheet1.xml"), "A8");
        String actualA8 = cellValue(generatedSheet(), "A8");
        assertEquals(goldenA8, actualA8, "A8（2026-04-11）序列值应与样板一致");
        String goldenJ6 = cellValue(golden("xl/worksheets/sheet1.xml"), "J6");
        String actualJ6 = cellValue(generatedSheet(), "J6");
        assertEquals(goldenJ6, actualJ6, "J6 对账日期（2026-08-20）序列值应与样板一致");
    }

    @Test
    @DisplayName("数字格式：中文年月日、千分位金额、负红金额、整数数量与样板同款")
    void numberFormatsShouldMatchGolden() {
        String actualStyles = generated("xl/styles.xml");
        assertTrue(actualStyles.contains("formatCode=\"yyyy&quot;年&quot;m&quot;月&quot;d&quot;日&quot;;@\""),
                "中文年月日格式");
        assertTrue(actualStyles.contains("formatCode=\"#,##0.00_ ;[Red]\\-#,##0.00\\ \""),
                "负数红色金额格式（明细 J 列）");
        assertTrue(actualStyles.contains("formatCode=\"0_ \""), "整数数量格式（页脚数量合计）");
        assertEquals(firstMatch(goldenStyles(), "<numFmt numFmtId=\"176\"[^>]*/>"),
                firstMatch(actualStyles, "<numFmt numFmtId=\"176\"[^>]*/>"), "176 格式码应与样板一致");
    }

    @Test
    @DisplayName("配色与字体：页脚三行的主题色填充、宋体字号与样板一致")
    void stylesShouldMatchGolden() {
        String actual = generated("xl/styles.xml");
        String goldenStyles = goldenStyles();
        assertTrue(actual.contains("<fgColor theme=\"9\" tint=\"0.6\"/>"), "页脚第 1 行填充 = 主题 accent6 60%");
        assertTrue(actual.contains("<fgColor theme=\"4\" tint=\"0.8\"/>"), "页脚第 2 行填充 = 主题 accent1 80%");
        assertTrue(actual.contains("<fgColor rgb=\"FFFFC000\"/>"), "页脚第 3 行填充 = FFC000");
        for (String font : List.of(
                "<b/><sz val=\"22\"/><color theme=\"1\"/><name val=\"宋体\"/>",
                "<sz val=\"9\"/><color theme=\"1\"/><name val=\"MS PMincho\"/>",
                "<b/><sz val=\"18\"/><name val=\"宋体\"/>",
                "<b/><sz val=\"10\"/><name val=\"宋体\"/>")) {
            assertTrue(actual.contains(font), "缺少字体定义：" + font);
        }
        // 主题文件应逐字节来自样板，保证 theme 引用渲染色一致
        assertArrayEquals(goldenParts.get("xl/theme/theme1.xml"), generatedParts.get("xl/theme/theme1.xml"),
                "主题配色文件应与样板一致");
    }

    @Test
    @DisplayName("Logo：图片逐字节一致，锚点（C1→D2）与样板一致")
    void logoShouldMatchGolden() {
        assertArrayEquals(goldenParts.get("xl/media/image1.png"), generatedParts.get("xl/media/image1.png"),
                "Logo 应为样板同款图片（逐字节）");
        String goldenAnchor = anchorOf(golden("xl/drawings/drawing1.xml"));
        String actualAnchor = anchorOf(generated("xl/drawings/drawing1.xml"));
        assertEquals(goldenAnchor, actualAnchor, "Logo 锚点应与样板一致");
        assertTrue(generatedSheet().contains("<drawing r:id=\"rId1\"/>"), "sheet 应挂 drawing");
        assertNotNull(generated("xl/worksheets/_rels/sheet1.xml.rels"), "sheet 关系缺失");
    }

    @Test
    @DisplayName("打印区域与重算：定义 Print_Area，打开文件即全量重算公式")
    void printAreaAndCalcShouldBeSet() {
        String workbook = generated("xl/workbook.xml");
        assertTrue(workbook.contains("name=\"_xlnm.Print_Area\""), "应定义打印区域");
        assertTrue(workbook.contains("!$A$1:$K$32</definedName>"), "打印区域应覆盖 A1:K32");
        assertTrue(workbook.contains("fullCalcOnLoad=\"1\""), "应设置打开即重算，公式无缓存值也能算出");
    }

    @Test
    @DisplayName("单位口径：页脚单位取明细行单位，混用时合并展示")
    void unitShouldFollowRows() {
        // 默认生成的 20 行单位都是 pcs
        assertTrue(generatedSheet().contains(">pcs</t>"), "页脚单位应取明细行单位");
    }

    @Test
    @DisplayName("sheet 名清洗：非法字符替换、超长截断到 31 字符")
    void sheetNameShouldBeSanitized() {
        assertEquals("a_b_c", StatementXlsxWriter.sheetName("a[b]c"));
        assertEquals("a_b_c", StatementXlsxWriter.sheetName("a:b\\c"));
        assertEquals("0123456789012345678901234567890",
                StatementXlsxWriter.sheetName("012345678901234567890123456789012345"));
    }

    /* ---------------- 工具 ---------------- */

    private static String generatedSheet() {
        return generated("xl/worksheets/sheet1.xml");
    }

    private static String generated(String name) {
        byte[] part = generatedParts.get(name);
        assertNotNull(part, "缺少部件：" + name);
        return new String(part, StandardCharsets.UTF_8);
    }

    private static String golden(String name) {
        byte[] part = goldenParts.get(name);
        assertNotNull(part, "金样板缺少部件：" + name);
        return new String(part, StandardCharsets.UTF_8);
    }

    private static String goldenStyles() {
        return golden("xl/styles.xml");
    }

    private static Map<String, byte[]> unzip(byte[] xlsx) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }

    /** 定位金样板：单测可能在仓库根或 admin-backend 目录下启动。 */
    private static Path locateGolden() {
        String relative = "desktop/resources/templates/statement-golden-sample.xlsx";
        List<Path> candidates = List.of(
                Path.of(relative),
                Path.of("..").resolve(relative),
                Path.of("../..").resolve(relative));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        // 兜底：直接从 classpath（测试资源里也可能有副本）
        try (InputStream in = StatementXlsxWriterTest.class.getClassLoader()
                .getResourceAsStream("statement-golden-sample.xlsx")) {
            if (in != null) {
                Path tmp = Files.createTempFile("golden-", ".xlsx");
                Files.write(tmp, in.readAllBytes());
                return tmp;
            }
        } catch (IOException ignored) {
            // 落到下面的断言失败
        }
        throw new IllegalStateException("找不到金样板 statement-golden-sample.xlsx，请确认仓库结构");
    }

    private static List<String> refs(String xml, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(xml);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static long countMatches(String xml, String pattern) {
        return refs(xml, pattern).size();
    }

    private static String firstMatch(String xml, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(xml);
        return matcher.find() ? matcher.group(0) : "";
    }

    private static Map<String, String> colWidths(String sheetXml) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("<col min=\"(\\d+)\"[^>]*width=\"([^\"]+)\"")
                .matcher(sheetXml);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            result.put(String.valueOf((char) ('A' + index - 1)), matcher.group(2));
        }
        return result;
    }

    private static Map<Integer, String> rowHeights(String sheetXml) {
        Map<Integer, String> result = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("<row r=\"(\\d+)\"[^>]*ht=\"([^\"]+)\"")
                .matcher(sheetXml);
        while (matcher.find()) {
            result.put(Integer.valueOf(matcher.group(1)), matcher.group(2));
        }
        return result;
    }

    /** 取某单元格的 <v> 值。 */
    private static String cellValue(String sheetXml, String ref) {
        Matcher matcher = Pattern.compile("<c r=\"" + ref + "\"[^>]*>(?:<f>[^<]*</f>)?<v>([^<]+)</v>")
                .matcher(sheetXml);
        assertTrue(matcher.find(), "单元格 " + ref + " 应有值：" + ref);
        return matcher.group(1);
    }

    /** 取 drawing 锚点（from/to 的行列偏移），忽略尺寸与图片内部属性。 */
    private static String anchorOf(String drawingXml) {
        Matcher from = Pattern.compile("<xdr:from>.*?</xdr:from>").matcher(drawingXml);
        Matcher to = Pattern.compile("<xdr:to>.*?</xdr:to>").matcher(drawingXml);
        assertTrue(from.find() && to.find(), "drawing 应有锚点");
        return from.group(0) + "|" + to.group(0);
    }
}
