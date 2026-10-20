package com.example.admin.infrastructure.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 极简 xlsx 写出器（JDK 自带 {@code java.util.zip} 实现，无三方依赖）。
 *
 * <p>为什么不用 Apache POI：xlsx 本质是"ZIP 包 + 若干 OOXML 零件"，
 * 而本系统需要的只是"多页签 × 表头 + 数据行 + 表头加粗"这一很小的子集。
 * 为此引入约 10MB 的 POI 依赖并不划算，也会让离线构建受制于仓库可达性。
 * 上层只依赖 {@link #write(List)}，将来若需要公式 / 图表 / .xls，替换本类即可，调用方零改动。
 *
 * <p>实现取舍：
 * <ul>
 *     <li>字符串用 inlineStr（{@code <is><t>}）而不是 sharedStrings，少一个零件、少一处出错点，
 *         Excel 与主流解析库均支持；</li>
 *     <li>能解析为数字的值按数值单元格写出，避免"数字被存成文本"导致的求和失灵；</li>
 *     <li>表头行用样式 1（加粗 + 底色 + 细边框），数据行用样式 0。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class XlsxWriter {

    private XlsxWriter() {
    }

    /**
     * 一个工作表的数据。
     *
     * @param name   表名（超过 31 字符会被截断，非法字符会被替换）
     * @param header 表头
     * @param rows   数据行（每行元素个数不足表头时补空）
     */
    public record Sheet(String name, List<String> header, List<List<String>> rows) {
    }

    /**
     * 生成 xlsx 字节流。
     *
     * @param sheets 工作表列表（至少 1 个）
     * @return xlsx 文件字节
     * @throws IOException 压缩失败时抛出
     */
    public static byte[] write(List<Sheet> sheets) throws IOException {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个工作表");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook(sheets));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
            put(zip, "xl/styles.xml", styles());
            for (int i = 0; i < sheets.size(); i++) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
            }
        }
        return buffer.toByteArray();
    }

    /* ---------------- 各零件 ---------------- */

    private static String contentTypes(int sheetCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        xml.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        xml.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        xml.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        xml.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        xml.append("</Types>");
        return xml.toString();
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        xml.append("<sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            xml.append("<sheet name=\"").append(escape(sheetName(sheets.get(i).name())))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        xml.append("</sheets></workbook>");
        return xml.toString();
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheetCount; i++) {
            xml.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"")
                    .append(" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        xml.append("<Relationship Id=\"rIdS\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        xml.append("</Relationships>");
        return xml.toString();
    }

    /**
     * 样式表：0=普通数据行，1=表头（加粗 + 浅灰底 + 细边框）。
     */
    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"等线\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"等线\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9E1F2\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left><right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top><bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
                + "</cellXfs>"
                + "</styleSheet>";
    }

    private static String sheetXml(Sheet sheet) {
        List<String> header = sheet.header() == null ? List.of() : sheet.header();
        List<List<String>> rows = sheet.rows() == null ? List.of() : sheet.rows();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append(colsXml(header, rows));
        xml.append("<sheetData>");
        xml.append(rowXml(1, header, true));
        for (int i = 0; i < rows.size(); i++) {
            xml.append(rowXml(i + 2, rows.get(i), false));
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static String colsXml(List<String> header, List<List<String>> rows) {
        StringBuilder xml = new StringBuilder("<cols>");
        for (int c = 0; c < header.size(); c++) {
            int max = header.get(c) == null ? 0 : header.get(c).length();
            for (List<String> row : rows) {
                if (c < row.size() && row.get(c) != null) {
                    max = Math.max(max, row.get(c).length());
                }
            }
            int width = Math.min(50, Math.max(10, max * 2 + 4));
            xml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        return xml.append("</cols>").toString();
    }

    private static String rowXml(int rowIndex, List<String> values, boolean isHeader) {
        StringBuilder xml = new StringBuilder();
        xml.append("<row r=\"").append(rowIndex).append("\">");
        for (int c = 0; c < values.size(); c++) {
            String value = values.get(c);
            if (value == null || value.isEmpty()) {
                continue;
            }
            xml.append("<c r=\"").append(columnLetter(c)).append(rowIndex).append("\"");
            if (isHeader) {
                xml.append(" s=\"1\"");
            }
            if (isNumeric(value)) {
                xml.append("><v>").append(value).append("</v></c>");
            } else {
                xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(value)).append("</t></is></c>");
            }
        }
        return xml.append("</row>").toString();
    }

    /* ---------------- 工具 ---------------- */

    /** 数值判断：整数或小数（不含千分位，写出时保持原始文本即可） */
    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // 以 0 开头且长度大于 1 的（如 "0012"）按文本处理，避免丢失前导零（料号常见）
        if (value.length() > 1 && value.charAt(0) == '0' && value.charAt(1) != '.') {
            return false;
        }
        try {
            Double.parseDouble(value);
            return value.matches("-?\\d+(\\.\\d+)?");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 列序号转 Excel 列标（0 → A，25 → Z，26 → AA）。
     *
     * @param index 从 0 开始的列序号
     * @return 列标
     */
    public static String columnLetter(int index) {
        StringBuilder builder = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int remainder = (n - 1) % 26;
            builder.insert(0, (char) ('A' + remainder));
            n = (n - 1) / 26;
        }
        return builder.toString();
    }

    /** 表名清洗：Excel 限制 31 字符且不允许 []:*?/\ */
    private static String sheetName(String name) {
        String cleaned = (name == null || name.isBlank()) ? "Sheet" : name.replaceAll("[]\\[:*?/\\\\]", "_").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&apos;");
                default -> {
                    // 控制字符在 XML 1.0 中非法，直接剔除，避免生成打不开的文件
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * 便捷构造：把字符串矩阵包成一个工作表。
     *
     * @param name   表名
     * @param header 表头
     * @param rows   数据行
     * @return 工作表
     */
    public static Sheet sheet(String name, List<String> header, List<List<String>> rows) {
        return new Sheet(name, new ArrayList<>(header), rows == null ? new ArrayList<>() : rows);
    }
}
