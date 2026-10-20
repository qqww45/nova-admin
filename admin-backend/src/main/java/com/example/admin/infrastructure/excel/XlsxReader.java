package com.example.admin.infrastructure.excel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 极简 xlsx 读取器（JDK 自带 {@code java.util.zip} + StAX 实现，无三方依赖）。
 *
 * <p>与 {@link XlsxWriter} 成对存在，理由相同：只支持本系统需要的子集 —— 读第一个（或指定）工作表、
 * 还原单元格文本、保留原始行号（错误清单要回指"第几行"）。
 *
 * <p>取值规则：
 * <ul>
 *     <li>{@code t="s"} 取 sharedStrings 中的第 N 项；{@code t="inlineStr"} 取 {@code <is><t>}；
 *         {@code t="str"} / {@code t="e"} 取 {@code <v>}；{@code t="b"} 转 true/false；</li>
 *     <li>默认（无 t 属性）为数值，直接取 {@code <v>} 文本并把 "12.0" 归一为 "12"，
 *         避免"整数被读成 12.0"污染千分位与幂等键；</li>
 *     <li>完全空白的行跳过，非空单元格少于 2 个的行视为空行（附录A A.1.3）。</li>
 * </ul>
 *
 * <p>日期单元格在 xlsx 里本质是带格式的数字，本类统一返回文本，
 * 由领域层 {@code ValueParser} 按目标字段类型解析（支持 Excel 日期序列）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class XlsxReader {

    private XlsxReader() {
    }

    /**
     * 一行数据。
     *
     * @param rowNumber 原始行号（与 Excel 一致，从 1 开始，含表头行）
     * @param cells     单元格文本（缺失的列为空串）
     */
    public record Row(int rowNumber, List<String> cells) {
    }

    /**
     * 读取工作簿中的工作表名（按 workbook.xml 顺序）。
     *
     * @param bytes xlsx 字节
     * @return 工作表名列表
     * @throws IOException 解析失败
     */
    public static List<String> sheetNames(byte[] bytes) throws IOException {
        Map<String, String> parts = readParts(bytes);
        String workbookXml = parts.get("xl/workbook.xml");
        if (workbookXml == null) {
            throw new IOException("不是合法的 xlsx 文件：缺少 xl/workbook.xml");
        }
        List<String> names = new ArrayList<>();
        XMLStreamReader reader = createReader(workbookXml);
        try {
            while (hasNext(reader)) {
                if (next(reader) == XMLStreamConstants.START_ELEMENT && "sheet".equals(reader.getLocalName())) {
                    String name = reader.getAttributeValue(null, "name");
                    if (name != null) {
                        names.add(name);
                    }
                }
            }
        } finally {
            closeQuietly(reader);
        }
        return names;
    }

    /**
     * 读取指定工作表的所有行（含表头行）。
     *
     * @param bytes     xlsx 字节
     * @param sheetName 工作表名；为空则读第一个工作表
     * @return 行列表（已跳过空行）
     * @throws IOException 解析失败或工作表不存在
     */
    public static List<Row> read(byte[] bytes, String sheetName) throws IOException {
        Map<String, String> parts = readParts(bytes);
        List<String> sharedStrings = parseSharedStrings(parts.get("xl/sharedStrings.xml"));
        String target = resolveSheetPart(parts, sheetName);
        String sheetXml = parts.get(target);
        if (sheetXml == null) {
            throw new IOException("工作表不存在：" + sheetName);
        }
        return filterRows(parseSheet(sheetXml, sharedStrings));
    }

    /**
     * 空行过滤（附录A A.1.3）：完全空白的行跳过且不报错。
     *
     * <p>为什么把"非空单元格少于 2 个"的判断放在这里而不是解析循环里：
     * 单列表（如只有一列的字典页、只有料号的一列数据）会被这条规则整表清空，
     * 因此只有在表头列数 &gt; 1 时才启用该规则。
     *
     * @param rows 原始行（含表头行）
     * @return 过滤后的行
     */
    private static List<Row> filterRows(List<Row> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        boolean singleColumn = rows.get(0).cells().size() <= 1;
        // 统一补到表头宽度：写入器会跳过空单元格，若不补齐，消费方每次取列都要判越界
        int width = rows.get(0).cells().size();
        List<Row> kept = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            // 表头行始终保留（它可能只有部分列有值）
            if (i == 0 || countNonEmpty(row.cells()) >= (singleColumn ? 1 : 2)) {
                kept.add(pad(row, width));
            }
        }
        return kept;
    }

    /**
     * 把行补到指定列数（右侧补空串）。
     *
     * @param row   原行
     * @param width 目标列数
     * @return 补齐后的行
     */
    private static Row pad(Row row, int width) {
        if (row.cells().size() >= width) {
            return row;
        }
        List<String> cells = new ArrayList<>(row.cells());
        while (cells.size() < width) {
            cells.add("");
        }
        return new Row(row.rowNumber(), cells);
    }

    /**
     * 读取第一个工作表。
     *
     * @param bytes xlsx 字节
     * @return 行列表
     * @throws IOException 解析失败
     */
    public static List<Row> read(byte[] bytes) throws IOException {
        return read(bytes, null);
    }

    /* ---------------- 内部实现 ---------------- */

    /** 把 zip 里需要的零件读成字符串（只关心 workbook / 关系 / 工作表 / 共享字符串） */
    private static Map<String, String> readParts(byte[] bytes) throws IOException {
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("xl/worksheets/") || "xl/workbook.xml".equals(name)
                        || "xl/sharedStrings.xml".equals(name) || name.endsWith("workbook.xml.rels")) {
                    parts.put(name, readAll(zip));
                }
            }
        }
        return parts;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static List<String> parseSharedStrings(String xml) throws IOException {
        List<String> items = new ArrayList<>();
        if (xml == null || xml.isEmpty()) {
            return items;
        }
        XMLStreamReader reader = createReader(xml);
        try {
            StringBuilder text = new StringBuilder();
            boolean inSi = false;
            boolean inT = false;
            while (hasNext(reader)) {
                int event = next(reader);
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("si".equals(local)) {
                        inSi = true;
                        text.setLength(0);
                    } else if ("t".equals(local) && inSi) {
                        inT = true;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && inT) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("t".equals(local)) {
                        inT = false;
                    } else if ("si".equals(local)) {
                        items.add(text.toString());
                        inSi = false;
                    }
                }
            }
        } finally {
            closeQuietly(reader);
        }
        return items;
    }

    /** 由工作表名解析出零件路径（走 workbook.xml.rels 的 rId 映射） */
    private static String resolveSheetPart(Map<String, String> parts, String sheetName) throws IOException {
        String relsKey = parts.keySet().stream().filter(k -> k.endsWith("workbook.xml.rels")).findFirst().orElse(null);
        Map<String, String> relTargets = new HashMap<>();
        if (relsKey != null) {
            XMLStreamReader reader = createReader(parts.get(relsKey));
            try {
                while (hasNext(reader)) {
                    if (next(reader) == XMLStreamConstants.START_ELEMENT
                            && "Relationship".equals(reader.getLocalName())) {
                        String id = reader.getAttributeValue(null, "Id");
                        String target = reader.getAttributeValue(null, "Target");
                        if (id != null && target != null) {
                            relTargets.put(id, normalizeTarget(target));
                        }
                    }
                }
            } finally {
                closeQuietly(reader);
            }
        }
        String workbookXml = parts.get("xl/workbook.xml");
        String wantedRid = null;
        String firstRid = null;
        XMLStreamReader reader = createReader(workbookXml == null ? "<workbook/>" : workbookXml);
        try {
            while (hasNext(reader)) {
                if (next(reader) == XMLStreamConstants.START_ELEMENT && "sheet".equals(reader.getLocalName())) {
                    String name = reader.getAttributeValue(null, "name");
                    String rid = reader.getAttributeValue(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                    if (rid == null) {
                        rid = reader.getAttributeValue(null, "r:id");
                    }
                    if (firstRid == null) {
                        firstRid = rid;
                    }
                    if (sheetName == null || sheetName.isBlank() || sheetName.equals(name)) {
                        wantedRid = rid;
                        break;
                    }
                }
            }
        } finally {
            closeQuietly(reader);
        }
        String rid = wantedRid != null ? wantedRid : firstRid;
        String target = rid == null ? null : relTargets.get(rid);
        if (target != null && parts.containsKey(target)) {
            return target;
        }
        // 兜底：直接取第一个工作表零件
        return parts.keySet().stream().filter(k -> k.startsWith("xl/worksheets/")).sorted().findFirst()
                .orElseThrow(() -> new IOException("工作簿中没有工作表"));
    }

    private static String normalizeTarget(String target) {
        String cleaned = target.replaceFirst("^\\./", "");
        if (cleaned.startsWith("/")) {
            return cleaned.substring(1);
        }
        return cleaned.startsWith("xl/") ? cleaned : "xl/" + cleaned;
    }

    /**
     * 解析工作表：按 {@code row} / {@code c} 结构还原单元格文本。
     *
     * <p>单元格类型在 {@code <c>} 的开始标签里拿到，用局部变量带到结束标签处使用 ——
     * 这样无需任何静态状态，方法内串行解析即可。
     */
    private static List<Row> parseSheet(String xml, List<String> sharedStrings) throws IOException {
        List<Row> rows = new ArrayList<>();
        XMLStreamReader reader = createReader(xml);
        try {
            List<String> current = null;
            int rowNumber = 0;
            StringBuilder text = new StringBuilder();
            boolean collectText = false;
            String cellType = "";
            int columnIndex = 0;

            while (hasNext(reader)) {
                int event = next(reader);
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "row" -> {
                            String r = reader.getAttributeValue(null, "r");
                            rowNumber = r == null ? rowNumber + 1 : parseIntOr(r, rowNumber + 1);
                            current = new ArrayList<>();
                            columnIndex = 0;
                        }
                        case "c" -> {
                            if (current == null) {
                                current = new ArrayList<>();
                            }
                            cellType = reader.getAttributeValue(null, "t");
                            if (cellType == null) {
                                cellType = "";
                            }
                            // 内联字符串在 <is><t> 里，其余类型取 <v>
                            collectText = "inlineStr".equals(cellType);
                            text.setLength(0);
                            String ref = reader.getAttributeValue(null, "r");
                            columnIndex = ref == null ? columnIndex : columnOf(ref);
                        }
                        case "t" -> {
                            if ("inlineStr".equals(cellType)) {
                                collectText = true;
                            }
                        }
                        case "v" -> collectText = true;
                        default -> {
                            // 其它元素忽略
                        }
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && collectText) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "t", "v" -> collectText = false;
                        case "c" -> {
                            String raw = text.toString();
                            String value = switch (cellType) {
                                case "s" -> resolveShared(sharedStrings, raw);
                                case "inlineStr" -> raw;
                                case "b" -> "1".equals(raw) ? "true" : "false";
                                default -> normalizeNumber(raw);
                            };
                            set(current, columnIndex, value);
                            columnIndex++;
                            text.setLength(0);
                        }
                        case "row" -> {
                            // 先全量收下，空行过滤交给 filterRows 统一处理（需要表头列数才能判断）
                            if (current != null) {
                                rows.add(new Row(rowNumber, current));
                            }
                            current = null;
                        }
                        default -> {
                            // 其它元素忽略
                        }
                    }
                }
            }
        } finally {
            closeQuietly(reader);
        }
        return rows;
    }

    private static String resolveShared(List<String> sharedStrings, String indexText) {
        try {
            int index = Integer.parseInt(indexText.trim());
            return (index >= 0 && index < sharedStrings.size()) ? sharedStrings.get(index) : "";
        } catch (NumberFormatException e) {
            return indexText;
        }
    }

    /** 数值归一：12.0 → 12，保留其它小数 */
    private static String normalizeNumber(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.matches("-?\\d+\\.0+")) {
            return text.substring(0, text.indexOf('.'));
        }
        return text;
    }

    private static void set(List<String> cells, int index, String value) {
        while (cells.size() <= index) {
            cells.add("");
        }
        cells.set(index, value == null ? "" : value);
    }

    private static int countNonEmpty(List<String> cells) {
        int count = 0;
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                count++;
            }
        }
        return count;
    }

    /** 由单元格引用（如 C5）取列序号 */
    private static int columnOf(String ref) {
        int index = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                index = index * 26 + (c - 'A' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, index - 1);
    }

    private static int parseIntOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 包装 {@code hasNext}：把受检的 {@link javax.xml.stream.XMLStreamException} 收敛为 IOException。
     *
     * <p>这样上层的解析循环就不必到处 try/catch，统一在 {@link #read} 边界向上抛。
     */
    private static boolean hasNext(XMLStreamReader reader) throws IOException {
        try {
            return reader.hasNext();
        } catch (javax.xml.stream.XMLStreamException e) {
            throw new IOException("XML 解析失败", e);
        }
    }

    /** 包装 {@code next}，理由同 {@link #hasNext}。 */
    private static int next(XMLStreamReader reader) throws IOException {
        try {
            return reader.next();
        } catch (javax.xml.stream.XMLStreamException e) {
            throw new IOException("XML 解析失败", e);
        }
    }

    private static XMLStreamReader createReader(String xml) throws IOException {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            // 关闭 DTD 与外部实体解析，防止 XXE
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            return factory.createXMLStreamReader(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("XML 解析失败", e);
        }
    }

    private static void closeQuietly(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (Exception ignored) {
            // 关闭失败不影响已解析结果
        }
    }
}
