package com.example.admin.infrastructure.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 金蝶对账单导出器 —— 按金样板（desktop/resources/templates/statement-golden-sample.xlsx，
 * 即「一恒科—鸿晟达」对帐单）逐格复刻版式。
 *
 * <p>为什么不用 {@link XlsxWriter}：那个类只覆盖「表头 + 数据行」的极简子集，
 * 而对账单是给客户签字的正式文件，需要合并单元格、行列尺寸、字体、主题色填充、
 * 页脚合计公式、打印设置与公司 Logo，必须精确复刻样板。
 *
 * <p>版式要点（与样板逐项对照过，改动前先看样板）：
 * <ul>
 *     <li>1~4 行：A1:K1 公司名（值尾带两个换行）→ A2 英文名 → A3 地址电话传真 → A4「对  帐  单」；</li>
 *     <li>5~6 行：客户名称 / 联系人 / 联系方式 / 地址 / 对账日期，无表格线；</li>
 *     <li>第 7 行：列头「日期 | 送货单号 | 客户订单号码 | 物料编号 | 物料名称 | 规格 | 单位 | 数量 | 单价 | 金额 | 备注」，
 *         外框双线、内线细线，K 列表头为「备注 + 该 sheet 物料标识」；</li>
 *     <li>明细行：金额列写 {@code =H*I} 公式（模板口径），日期为 Excel 日期序列 + 中文年月日格式；</li>
 *     <li>页脚三行：累计订单送货（数量合计）/ 累计货款（含税）（金额合计）/ 累计已收款金额（留空，财务手填）/
 *         累计应收款金额（= 货款 − 已收），配色与样板一致，左侧 A:C 三行合并为盖章区；</li>
 *     <li>末两行：确认签字提示 + 制单 / 业务 / 审核 / 客户签字；</li>
 *     <li>A4 横向、94% 缩放、水平居中打印，打印区域 A1:K{末行}；</li>
 *     <li>公司 Logo 图片锚在 C1:D2（随包资源 export/statement-logo.png）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
public final class StatementXlsxWriter {

    private StatementXlsxWriter() {
    }

    /** 对账单工作表定义。 */
    public record Sheet(String name, Header header, String remarkSuffix,
                        String preparer, String salesperson, List<Row> rows) {
    }

    /**
     * 抬头信息。
     *
     * @param companyName   公司名（A1，输出时尾随两个换行，与样板一致）
     * @param companyEnName 公司英文名（A2）
     * @param companyInfo   地址 / 电话 / 传真整行（A3）
     * @param customerName  客户名称（B5）
     * @param customerContact 客户联系人（F5；采集链路未同步时为空）
     * @param customerPhone   客户联系方式（J5；采集链路未同步时为空）
     * @param customerAddress 客户地址（B6）
     * @param statementDate   对账日期（J6）
     */
    public record Header(String companyName, String companyEnName, String companyInfo,
                         String customerName, String customerContact, String customerPhone,
                         String customerAddress, LocalDate statementDate) {
    }

    /**
     * 一行送货明细。
     *
     * @param date          送货日期
     * @param deliveryNo    送货单号
     * @param customerPo    客户订单号码
     * @param materialNumber 物料编号
     * @param materialName  物料名称
     * @param spec          规格
     * @param unit          单位
     * @param qty           数量；为 null 表示该行无数（无分录单据退回表头金额的行）
     * @param price         单价（含税）；为 null 同上
     * @param directAmount  无数量 / 单价时直填的金额（J 列写值而不是公式）
     * @param remark        备注
     */
    public record Row(LocalDate date, String deliveryNo, String customerPo,
                      String materialNumber, String materialName, String spec, String unit,
                      BigDecimal qty, BigDecimal price, BigDecimal directAmount, String remark) {
    }

    /* ---------------- 样式注册表 ---------------- */

    private static final String[] NUM_FLEX_XML = {
            "<numFmt numFmtId=\"176\" formatCode=\"yyyy&quot;年&quot;m&quot;月&quot;d&quot;日&quot;;@\"/>",
            "<numFmt numFmtId=\"177\" formatCode=\"#,##0.00_ ;[Red]\\-#,##0.00\\ \"/>",
            "<numFmt numFmtId=\"178\" formatCode=\"m&quot;月&quot;d&quot;日&quot;;@\"/>",
            "<numFmt numFmtId=\"179\" formatCode=\"0_ \"/>",
    };

    private static final String FMT_DATE_CN = "176";
    private static final String FMT_MONEY_RED = "177";
    private static final String FMT_MONEY = "4";
    private static final String FMT_QTY = "179";

    private static final String FONT_DEFAULT = "宋体";
    private static final String FONT_EN = "MS PMincho";

    /** 样式注册表：键 → cellXfs 序号（构建期填好，保证输出确定）。 */
    private static final Map<String, Integer> XF_INDEX = new LinkedHashMap<>();
    private static final List<String> XF_XML = new ArrayList<>();

    private static final int XF_COMPANY_NAME;
    private static final int XF_COMPANY_EN;
    private static final int XF_COMPANY_INFO;
    private static final int XF_TITLE;
    private static final int XF_LABEL_BOLD;
    private static final int XF_LABEL_BOLD_DATE;
    private static final int XF_HEAD_A;
    private static final int XF_HEAD_TEXT;
    private static final int XF_HEAD_TEXT_AT;
    private static final int XF_HEAD_MONEY;
    private static final int XF_HEAD_REMARK;
    private static final int[] XF_DETAIL = new int[11];
    private static final int[] XF_FOOT1 = new int[11];
    private static final int[] XF_FOOT2 = new int[11];
    private static final int[] XF_FOOT3 = new int[11];
    private static final int XF_NOTE;
    private static final int XF_SIGN_LEFT;
    private static final int XF_SIGN_CENTER;
    private static final int XF_SIGN_BLANK;

    /** Excel 日期序列的纪元（1900 闰年 bug 修正后的起点）。 */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    static {
        // 字体序号：0=宋体11（默认）1=宋体22b 2=MS PMincho9 3=宋体9 4=宋体18b 5=宋体10b 6=宋体10
        XF_COMPANY_NAME = xf("0", "1", "0", "0", "center", "center", true);
        XF_COMPANY_EN = xf("0", "2", "0", "0", "center", "center", true);
        XF_COMPANY_INFO = xf("0", "3", "0", "0", "center", "center", true);
        XF_TITLE = xf("0", "4", "0", "0", "center", null, false);
        XF_LABEL_BOLD = xf("0", "5", "0", "0", "left", "center", false);
        XF_LABEL_BOLD_DATE = xf(FMT_DATE_CN, "5", "0", "0", "left", "center", false);

        XF_HEAD_A = xf("0", "5", "0", "5", "center", "center", false);
        XF_HEAD_TEXT = xf("0", "5", "0", "4", "center", "center", false);
        XF_HEAD_TEXT_AT = xf("49", "5", "0", "4", "center", "center", false);
        XF_HEAD_MONEY = xf(FMT_MONEY, "5", "0", "4", "center", "center", false);
        XF_HEAD_REMARK = xf("0", "5", "0", "6", "left", "center", true);

        // 明细行：A 日期（左 + 换行），B/C/D 左，E/G 居中不换行，F/H/I/J 居中换行，K 左 + 换行
        XF_DETAIL[0] = xf(FMT_DATE_CN, "3", "0", "2", "left", "center", true);
        XF_DETAIL[1] = xf("0", "3", "0", "1", "left", "center", true);
        XF_DETAIL[2] = xf("0", "3", "0", "1", "left", "center", true);
        XF_DETAIL[3] = xf("0", "3", "0", "1", "left", "center", true);
        XF_DETAIL[4] = xf("0", "3", "0", "1", "center", "center", false);
        XF_DETAIL[5] = xf("0", "3", "0", "1", "center", "center", true);
        XF_DETAIL[6] = xf("0", "3", "0", "1", "center", "center", false);
        XF_DETAIL[7] = xf("0", "3", "0", "1", "center", "center", true);
        XF_DETAIL[8] = xf(FMT_MONEY, "3", "0", "1", "center", "center", true);
        XF_DETAIL[9] = xf(FMT_MONEY_RED, "3", "0", "1", "center", "center", true);
        XF_DETAIL[10] = xf("0", "3", "0", "3", "left", "center", true);

        // 页脚第 1 行（累计订单送货 / 累计货款）：填充 = 主题 accent6 60% 变浅
        XF_FOOT1[0] = xf("178", "5", "0", "7", "center", "center", false);
        XF_FOOT1[1] = xf("0", "0", "0", "8", null, null, false);
        XF_FOOT1[2] = xf("0", "0", "0", "9", null, null, false);
        XF_FOOT1[3] = xf("178", "5", "2", "1", "center", "center", false);
        XF_FOOT1[4] = xf("0", "0", "0", "19", null, null, false);
        XF_FOOT1[5] = xf("0", "0", "0", "20", null, null, false);
        XF_FOOT1[6] = xf("0", "5", "2", "1", "center", "center", false);
        XF_FOOT1[7] = xf(FMT_QTY, "5", "2", "1", "center", "center", false);
        XF_FOOT1[8] = xf(FMT_MONEY, "5", "2", "1", "center", "center", true);
        XF_FOOT1[9] = xf(FMT_MONEY, "5", "2", "1", "center", "center", false);
        XF_FOOT1[10] = xf(FMT_MONEY, "6", "2", "3", "left", "center", true);

        // 页脚第 2 行（累计已收款金额，留空手填）：填充 = 主题 accent1 80% 变浅
        XF_FOOT2[0] = xf("0", "0", "0", "10", null, null, false);
        XF_FOOT2[1] = xf("0", "0", "0", "0", null, null, false);
        XF_FOOT2[2] = xf("0", "0", "0", "11", null, null, false);
        XF_FOOT2[3] = xf(FMT_MONEY, "5", "3", "1", "center", "center", false);
        XF_FOOT2[4] = xf("0", "0", "0", "19", null, null, false);
        XF_FOOT2[5] = xf("0", "0", "0", "20", null, null, false);
        XF_FOOT2[6] = xf(FMT_MONEY, "5", "3", "22", "center", "center", false);
        XF_FOOT2[7] = xf("0", "0", "0", "16", null, null, false);
        XF_FOOT2[8] = xf("0", "0", "0", "16", null, null, false);
        XF_FOOT2[9] = xf("0", "0", "0", "17", null, null, false);
        XF_FOOT2[10] = xf(FMT_MONEY, "6", "3", "3", null, "center", true);

        // 页脚第 3 行（累计应收款金额）：填充 = 橙色 FFC000，整行下边框双线收尾
        XF_FOOT3[0] = xf("0", "0", "0", "12", null, null, false);
        XF_FOOT3[1] = xf("0", "0", "0", "13", null, null, false);
        XF_FOOT3[2] = xf("0", "0", "0", "14", null, null, false);
        XF_FOOT3[3] = xf(FMT_MONEY, "5", "4", "15", "center", "center", false);
        XF_FOOT3[4] = xf("0", "0", "0", "16", null, null, false);
        XF_FOOT3[5] = xf("0", "0", "0", "17", null, null, false);
        XF_FOOT3[6] = xf(FMT_MONEY, "5", "4", "15", "center", "center", false);
        XF_FOOT3[7] = xf("0", "0", "0", "16", null, null, false);
        XF_FOOT3[8] = xf("0", "0", "0", "16", null, null, false);
        XF_FOOT3[9] = xf("0", "0", "0", "17", null, null, false);
        XF_FOOT3[10] = xf(FMT_MONEY, "3", "4", "18", null, "center", true);

        XF_NOTE = xf("178", "5", "0", "0", "left", "center", false);
        XF_SIGN_LEFT = xf("0", "5", "0", "0", "left", "center", false);
        XF_SIGN_CENTER = xf("0", "5", "0", "0", "center", "center", false);
        XF_SIGN_BLANK = xf("0", "0", "0", "0", null, null, false);
    }

    /**
     * 注册一个 cellXf（幂等）。
     *
     * @param numFmt 数字格式（"0" General）
     * @param font   字体序号
     * @param fill   填充序号
     * @param border 边框序号
     * @param h      水平对齐（null = 默认）
     * @param v      垂直对齐（null = 默认）
     * @param wrap    自动换行
     * @return cellXfs 序号
     */
    private static int xf(String numFmt, String font, String fill, String border,
                          String h, String v, boolean wrap) {
        String key = String.join("|", numFmt, font, fill, border, h, v, String.valueOf(wrap));
        return XF_INDEX.computeIfAbsent(key, k -> {
            StringBuilder xml = new StringBuilder("<xf numFmtId=\"").append(numFmt)
                    .append("\" fontId=\"").append(font)
                    .append("\" fillId=\"").append(fill)
                    .append("\" borderId=\"").append(border)
                    .append("\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\"")
                    .append(" applyBorder=\"1\" applyAlignment=\"1\">");
            if (h != null || v != null || wrap) {
                xml.append("<alignment");
                if (h != null) {
                    xml.append(" horizontal=\"").append(h).append("\"");
                }
                if (v != null) {
                    xml.append(" vertical=\"").append(v).append("\"");
                }
                if (wrap) {
                    xml.append(" wrapText=\"1\"");
                }
                xml.append("/>");
            }
            xml.append("</xf>");
            XF_XML.add(xml.toString());
            return XF_XML.size() - 1;
        });
    }

    /* ---------------- 导出 ---------------- */

    /**
     * 生成对账单 xlsx。
     *
     * @param sheets 工作表（至少 1 个）
     * @return 文件字节
     * @throws IOException 写 zip 失败
     */
    public static byte[] write(List<Sheet> sheets) throws IOException {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个工作表");
        }
        byte[] logo = readResource("export/statement-logo.png");
        byte[] theme = readResource("export/statement-theme.xml");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook(sheets));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/theme/theme1.xml", theme);
            put(zip, "xl/media/image1.png", logo);
            for (int i = 0; i < sheets.size(); i++) {
                int no = i + 1;
                put(zip, "xl/worksheets/sheet" + no + ".xml", sheetXml(sheets.get(i)));
                put(zip, "xl/worksheets/_rels/sheet" + no + ".xml.rels", sheetRels(no));
                put(zip, "xl/drawings/drawing" + no + ".xml", drawing());
                put(zip, "xl/drawings/_rels/drawing" + no + ".xml.rels", drawingRels());
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
        xml.append("<Default Extension=\"png\" ContentType=\"image/png\"/>");
        xml.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        xml.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        xml.append("<Override PartName=\"/xl/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            xml.append("<Override PartName=\"/xl/drawings/drawing").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
        }
        return xml.append("</Types>").toString();
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
        xml.append("<workbookPr/>");
        xml.append("<sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            xml.append("<sheet name=\"").append(escape(sheets.get(i).name()))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        xml.append("</sheets>");
        xml.append("<definedNames>");
        for (int i = 0; i < sheets.size(); i++) {
            Sheet sheet = sheets.get(i);
            int last = lastRow(sheet);
            xml.append("<definedName name=\"_xlnm.Print_Area\" localSheetId=\"").append(i).append("\">")
                    .append('\'').append(escape(sheet.name().replace("'", "''"))).append('\'')
                    .append("!$A$1:$K$").append(last)
                    .append("</definedName>");
        }
        xml.append("</definedNames>");
        // 打开即全量重算：金额 / 合计全部是公式，不缓存值也能在 Excel / WPS 里立即算出
        xml.append("<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/>");
        return xml.append("</workbook>").toString();
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
        xml.append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        xml.append("<Relationship Id=\"rIdTheme\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>");
        return xml.append("</Relationships>").toString();
    }

    private static String sheetRels(int no) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\""
                + " Target=\"../drawings/drawing" + no + ".xml\"/>"
                + "</Relationships>";
    }

    /**
     * Logo 锚点与样板完全一致：C1 起 D2 止，oneCell 锚定；白色做透明（clrChange）。
     */
    private static String drawing() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                + "<xdr:twoCellAnchor editAs=\"oneCell\">"
                + "<xdr:from><xdr:col>2</xdr:col><xdr:colOff>697230</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>635</xdr:rowOff></xdr:from>"
                + "<xdr:to><xdr:col>3</xdr:col><xdr:colOff>271145</xdr:colOff><xdr:row>1</xdr:row><xdr:rowOff>137160</xdr:rowOff></xdr:to>"
                + "<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"2\" name=\"Picture 1\"/><xdr:cNvPicPr/></xdr:nvPicPr>"
                + "<xdr:blipFill><a:blip r:embed=\"rId1\">"
                + "<a:clrChange><a:clrFrom><a:srgbClr val=\"FFFFFF\"><a:alpha val=\"100000\"/></a:srgbClr></a:clrFrom>"
                + "<a:clrTo><a:srgbClr val=\"FFFFFF\"><a:alpha val=\"0\"/></a:srgbClr></a:clrTo></a:clrChange>"
                + "</a:blip><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>"
                + "<xdr:spPr><a:xfrm><a:off x=\"2757805\" y=\"635\"/><a:ext cx=\"539750\" cy=\"492125\"/></a:xfrm>"
                + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/>"
                + "<a:ln w=\"9525\"><a:noFill/></a:ln></xdr:spPr></xdr:pic>"
                + "<xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>";
    }

    private static String drawingRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\""
                + " Target=\"../media/image1.png\"/>"
                + "</Relationships>";
    }

    private static String styles() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<numFmts count=\"").append(NUM_FLEX_XML.length).append("\">");
        for (String fmt : NUM_FLEX_XML) {
            xml.append(fmt);
        }
        xml.append("</numFmts>");
        // 0=宋体11 默认 1=宋体22b 公司名 2=MS PMincho9 英文名 3=宋体9 明细
        // 4=宋体18b 标题 5=宋体10b 标签/表头/页脚 6=宋体10 页脚备注
        xml.append("<fonts count=\"7\">");
        xml.append("<font><sz val=\"11\"/><color theme=\"1\"/><name val=\"宋体\"/><charset val=\"134\"/></font>");
        xml.append("<font><b/><sz val=\"22\"/><color theme=\"1\"/><name val=\"宋体\"/><charset val=\"134\"/></font>");
        xml.append("<font><sz val=\"9\"/><color theme=\"1\"/><name val=\"MS PMincho\"/><charset val=\"134\"/></font>");
        xml.append("<font><sz val=\"9\"/><color theme=\"1\"/><name val=\"宋体\"/><charset val=\"134\"/></font>");
        xml.append("<font><b/><sz val=\"18\"/><name val=\"宋体\"/><charset val=\"134\"/></font>");
        xml.append("<font><b/><sz val=\"10\"/><name val=\"宋体\"/><charset val=\"134\"/></font>");
        xml.append("<font><sz val=\"10\"/><name val=\"宋体\"/><charset val=\"134\"/></font>");
        xml.append("</fonts>");
        // 0=无 1=灰纹（规范要求） 2=accent6 60% 变浅 3=accent1 80% 变浅 4=橙色 FFC000
        xml.append("<fills count=\"5\">");
        xml.append("<fill><patternFill patternType=\"none\"/></fill>");
        xml.append("<fill><patternFill patternType=\"gray125\"/></fill>");
        xml.append("<fill><patternFill patternType=\"solid\"><fgColor theme=\"9\" tint=\"0.6\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        xml.append("<fill><patternFill patternType=\"solid\"><fgColor theme=\"4\" tint=\"0.8\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        xml.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFC000\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        xml.append("</fills>");
        xml.append(borders());
        xml.append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>");
        xml.append("<cellXfs count=\"").append(XF_XML.size()).append("\">");
        for (String xf : XF_XML) {
            xml.append(xf);
        }
        xml.append("</cellXfs>");
        xml.append("<cellStyles count=\"1\"><cellStyle name=\"常规\" xfId=\"0\" builtinId=\"0\"/></cellStyles>");
        return xml.append("</styleSheet>").toString();
    }

    /**
     * 边框序号约定（构建样式表时引用）：
     * 0=无 1=四边细线 2=左边双线其余细线（A 列） 3=右边双线其余细线（K 列）
     * 4=上边双线其余细线（表头 B~J） 5=左上双线（A7） 6=右上双线（K7）
     * 7~11=盖章区 A{f}/B{f}/C{f}/A{f+1}/C{f+1} 的可见边
     * 12~14=盖章区 A{f+2}/B{f+2}/C{f+2}；15=下双线标签；16=上下细线下双线；17=右细线下双线
     * 18=K 列收尾（右双线下双线） 19=上下细线 20=右细线三边细线 22=已收金额格（下边双线）
     */
    private static String borders() {
        StringBuilder xml = new StringBuilder();
        List<String> list = new ArrayList<>();
        // 0 无边框
        list.add("<border><left/><right/><top/><bottom/><diagonal/></border>");
        // 1 四边细线
        list.add(borderXml("thin", "thin", "thin", "thin"));
        // 2 A 列（左双线）
        list.add(borderXml("double", "thin", "thin", "thin"));
        // 3 K 列（右双线）
        list.add(borderXml("thin", "double", "thin", "thin"));
        // 4 表头 B~J（上双线）
        list.add(borderXml("thin", "thin", "double", "thin"));
        // 5 A7（左 + 上双线）
        list.add(borderXml("double", "thin", "double", "thin"));
        // 6 K7（右 + 上双线）
        list.add(borderXml("thin", "double", "double", "thin"));
        // 7 A{f}：左双线 + 上细线
        list.add(borderXml("double", null, "thin", null));
        // 8 B{f}：上细线
        list.add(borderXml(null, null, "thin", null));
        // 9 C{f}：右 + 上细线
        list.add(borderXml(null, "thin", "thin", null));
        // 10 A{f+1}：左双线
        list.add(borderXml("double", null, null, null));
        // 11 C{f+1}：右细线
        list.add(borderXml(null, "thin", null, null));
        // 12 A{f+2}：左双线 + 下双线
        list.add(borderXml("double", null, null, "double"));
        // 13 B{f+2}：下双线
        list.add(borderXml(null, null, null, "double"));
        // 14 C{f+2}：右细线 + 下双线
        list.add(borderXml(null, "thin", null, "double"));
        // 15 页脚底行标签 / 金额格：四边细线、下边双线
        list.add(borderXml("thin", "thin", "thin", "double"));
        // 16 页脚底行中段：上细线 + 下双线
        list.add(borderXml(null, null, "thin", "double"));
        // 17 页脚底行右段：右细线 + 上细线 + 下双线
        list.add(borderXml(null, "thin", "thin", "double"));
        // 18 K{f+2}：左细线、右双线、上细线、下双线
        list.add(borderXml("thin", "double", "thin", "double"));
        // 19 E{f}/E{f+1}：上细线 + 下细线
        list.add(borderXml(null, null, "thin", "thin"));
        // 20 F{f}/F{f+1}：右细线 + 上细线 + 下细线
        list.add(borderXml(null, "thin", "thin", "thin"));
        // 21 预留（保持序号稳定）
        list.add(borderXml(null, null, null, null));
        // 22 已收金额格 G{f+1}:J{f+1}（合并）：四边细线但下边双线
        list.add(borderXml("thin", "thin", "thin", "double"));
        xml.append("<borders count=\"").append(list.size()).append("\">");
        for (String b : list) {
            xml.append(b);
        }
        return xml.append("</borders>").toString();
    }

    private static String borderXml(String left, String right, String top, String bottom) {
        StringBuilder xml = new StringBuilder("<border>");
        xml.append(edge("left", left)).append(edge("right", right))
                .append(edge("top", top)).append(edge("bottom", bottom));
        return xml.append("<diagonal/></border>").toString();
    }

    private static String edge(String name, String style) {
        return style == null
                ? "<" + name + "/>"
                : "<" + name + " style=\"" + style + "\"><color auto=\"1\"/></" + name + ">";
    }

    /* ---------------- 工作表 ---------------- */

    /** 模板列宽（金样板 sheet「0024 」实测值）。 */
    private static final double[] COL_WIDTHS =
            {12.25, 14.7916666666667, 12.675, 12.875, 11, 25.875, 4.31666666666667,
                    6.375, 9.375, 11.5, 35.0583333333333};

    private static int lastRow(Sheet sheet) {
        return 8 + Math.max(0, sheet.rows().size()) + 4;
    }

    private static String sheetXml(Sheet sheet) {
        int n = sheet.rows().size();
        int f = 8 + n;
        int note = f + 3;
        int sig = f + 4;
        Header h = sheet.header();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        xml.append("<sheetFormatPr defaultColWidth=\"9\" defaultRowHeight=\"13.5\"/>");
        xml.append("<cols>");
        for (int c = 0; c < COL_WIDTHS.length; c++) {
            xml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(num(COL_WIDTHS[c])).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols>");
        xml.append("<sheetData>");

        // ---- 抬头 ----
        row(xml, 1, 28, cells(1)
                .text(0, XF_COMPANY_NAME, h.companyName() + "\n\n"));
        row(xml, 2, 12, cells(2).text(0, XF_COMPANY_EN, h.companyEnName()));
        row(xml, 3, 12, cells(3).text(0, XF_COMPANY_INFO, h.companyInfo()));
        row(xml, 4, 25, cells(4).text(0, XF_TITLE, "对  帐  单"));
        Cells row5 = cells(5)
                .text(0, XF_LABEL_BOLD, "客户名称：")
                .text(1, XF_LABEL_BOLD, h.customerName())
                .text(4, XF_LABEL_BOLD, "联系人:")
                .text(5, XF_LABEL_BOLD, h.customerContact())
                .text(8, XF_LABEL_BOLD, "联系方式：")
                .text(9, XF_LABEL_BOLD, h.customerPhone());
        row(xml, 5, 20, row5);
        Cells row6 = cells(6)
                .text(0, XF_LABEL_BOLD, "地址:")
                .text(1, XF_LABEL_BOLD, h.customerAddress())
                .text(8, XF_LABEL_BOLD, "对账日期：");
        if (h.statementDate() != null) {
            row6.number(9, XF_LABEL_BOLD_DATE, dateSerial(h.statementDate()));
        }
        row(xml, 6, 20, row6);

        // ---- 列头 ----
        Cells head = cells(7)
                .text(0, XF_HEAD_A, "日期")
                .text(1, XF_HEAD_TEXT_AT, "送货单号")
                .text(2, XF_HEAD_TEXT, "客户订单号码")
                .text(3, XF_HEAD_TEXT, "物料编号")
                .text(4, XF_HEAD_TEXT, "物料名称")
                .text(5, XF_HEAD_TEXT, "规格")
                .text(6, XF_HEAD_TEXT, "单位")
                .text(7, XF_HEAD_TEXT, "数量")
                .text(8, XF_HEAD_MONEY, "单价")
                .text(9, XF_HEAD_MONEY, "金额")
                .text(10, XF_HEAD_REMARK, "备注" + nullToEmpty(sheet.remarkSuffix()));
        row(xml, 7, 30, head);

        // ---- 明细 ----
        for (int i = 0; i < n; i++) {
            Row r = sheet.rows().get(i);
            int rn = 8 + i;
            Cells cells = cells(rn);
            if (r.date() != null) {
                cells.number(0, XF_DETAIL[0], dateSerial(r.date()));
            } else {
                cells.empty(0, XF_DETAIL[0]);
            }
            cells.text(1, XF_DETAIL[1], r.deliveryNo());
            cells.text(2, XF_DETAIL[2], r.customerPo());
            cells.text(3, XF_DETAIL[3], r.materialNumber());
            cells.text(4, XF_DETAIL[4], r.materialName());
            cells.text(5, XF_DETAIL[5], r.spec());
            cells.text(6, XF_DETAIL[6], r.unit());
            if (r.qty() != null) {
                cells.number(7, XF_DETAIL[7], number(r.qty()));
            } else {
                cells.empty(7, XF_DETAIL[7]);
            }
            if (r.price() != null) {
                cells.number(8, XF_DETAIL[8], number(r.price()));
            } else {
                cells.empty(8, XF_DETAIL[8]);
            }
            if (r.qty() != null && r.price() != null) {
                // 模板口径：金额 = 数量 × 单价，写公式让 Excel 重算并联动页脚合计
                cells.formula(9, XF_DETAIL[9], "H" + rn + "*I" + rn);
            } else if (r.directAmount() != null) {
                // 无分录单据退回表头金额：J 直填值，保证页脚 SUM 仍然正确
                cells.number(9, XF_DETAIL[9], number(r.directAmount()));
            } else {
                cells.empty(9, XF_DETAIL[9]);
            }
            cells.text(10, XF_DETAIL[10], r.remark());
            row(xml, rn, 23, cells);
        }

        // ---- 页脚 ----
        String unit = unitOf(sheet.rows());
        Cells foot1 = cells(f)
                .empty(0, XF_FOOT1[0])
                .empty(1, XF_FOOT1[1])
                .empty(2, XF_FOOT1[2])
                .text(3, XF_FOOT1[3], "累计订单送货 ")
                .empty(4, XF_FOOT1[4])
                .empty(5, XF_FOOT1[5])
                .text(6, XF_FOOT1[6], unit)
                .formula(7, XF_FOOT1[7], n > 0 ? "SUM(H8:H" + (f - 1) + ")" : "0")
                .text(8, XF_FOOT1[8], "累计货款（含税）")
                .formula(9, XF_FOOT1[9], n > 0 ? "SUM(J8:J" + (f - 1) + ")" : "0");
        row(xml, f, 30, foot1);

        Cells foot2 = cells(f + 1)
                .empty(0, XF_FOOT2[0])
                .empty(1, XF_FOOT2[1])
                .empty(2, XF_FOOT2[2])
                .text(3, XF_FOOT2[3], "累计已收款金额")
                .empty(4, XF_FOOT2[4])
                .empty(5, XF_FOOT2[5])
                // 已收金额由财务手工填入（样板 0233 页同样留空），应收公式按空值当 0 计算
                .empty(6, XF_FOOT2[6])
                .empty(7, XF_FOOT2[7])
                .empty(8, XF_FOOT2[8])
                .empty(9, XF_FOOT2[9])
                .empty(10, XF_FOOT2[10]);
        row(xml, f + 1, 30, foot2);

        Cells foot3 = cells(f + 2)
                .empty(0, XF_FOOT3[0])
                .empty(1, XF_FOOT3[1])
                .empty(2, XF_FOOT3[2])
                .text(3, XF_FOOT3[3], "累计应收款金额")
                .empty(4, XF_FOOT3[4])
                .empty(5, XF_FOOT3[5])
                .formula(6, XF_FOOT3[6], "J" + f + "-G" + (f + 1))
                .empty(7, XF_FOOT3[7])
                .empty(8, XF_FOOT3[8])
                .empty(9, XF_FOOT3[9])
                .empty(10, XF_FOOT3[10]);
        row(xml, f + 2, 35, foot3);

        row(xml, note, 16, cells(note).text(0, XF_NOTE,
                "注：请确认以上信息，并签字盖章回传，谢谢！"));

        Cells sign = cells(sig)
                .text(0, XF_SIGN_LEFT, "制单：" + nullToEmpty(sheet.preparer()))
                .text(2, XF_SIGN_LEFT, "业务：" + nullToEmpty(sheet.salesperson()))
                .text(4, XF_SIGN_LEFT, "审核：")
                .empty(5, XF_SIGN_BLANK)
                .empty(6, XF_SIGN_BLANK)
                .text(8, XF_SIGN_CENTER, "客户签字/盖章:");
        row(xml, sig, 23, sign);

        xml.append("</sheetData>");

        // ---- 合并（与样板同构，行号随明细行数平移）----
        List<String> merges = List.of(
                "A1:K1", "A2:K2", "A3:K3", "A4:K4",
                "B5:D5", "F5:H5", "J5:K5", "B6:H6", "J6:K6",
                "A" + f + ":C" + (f + 2),
                "D" + f + ":F" + f, "D" + (f + 1) + ":F" + (f + 1), "G" + (f + 1) + ":J" + (f + 1),
                "D" + (f + 2) + ":F" + (f + 2), "G" + (f + 2) + ":J" + (f + 2),
                "A" + note + ":K" + note, "F" + sig + ":G" + sig);
        xml.append("<mergeCells count=\"").append(merges.size()).append("\">");
        for (String m : merges) {
            xml.append("<mergeCell ref=\"").append(m).append("\"/>");
        }
        xml.append("</mergeCells>");
        xml.append("<printOptions horizontalCentered=\"1\"/>");
        xml.append("<pageMargins left=\"0.196527777777778\" right=\"0.196527777777778\""
                + " top=\"0.393055555555556\" bottom=\"0.393055555555556\" header=\"0\" footer=\"0\"/>");
        xml.append("<pageSetup paperSize=\"9\" scale=\"94\" orientation=\"landscape\" horizontalDpi=\"600\"/>");
        xml.append("<drawing r:id=\"rId1\"/>");
        return xml.append("</worksheet>").toString();
    }

    /** 页脚数量格的单位：样板固定 pcs，这里取该 sheet 明细行的实际单位（不一致时合并展示）。 */
    private static String unitOf(List<Row> rows) {
        List<String> units = rows.stream().map(Row::unit)
                .filter(u -> u != null && !u.isBlank()).distinct().toList();
        if (units.isEmpty()) {
            return "pcs";
        }
        return String.join("/", units);
    }

    /* ---------------- 行 / 单元格写出 ---------------- */

    private static final String[] COLS =
            {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"};

    /** 一行的待写单元格（构建后一次性渲染；记录行号用于拼单元格引用）。 */
    private static final class Cells {
        private final int row;
        private final StringBuilder xml = new StringBuilder();

        private Cells(int row) {
            this.row = row;
        }

        private String ref(int col) {
            return COLS[col] + row;
        }

        Cells empty(int col, int style) {
            xml.append("<c r=\"").append(ref(col)).append("\" s=\"").append(style).append("\"/>");
            return this;
        }

        Cells text(int col, int style, String value) {
            if (value == null || value.isEmpty()) {
                return empty(col, style);
            }
            xml.append("<c r=\"").append(ref(col)).append("\" s=\"").append(style)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(value)).append("</t></is></c>");
            return this;
        }

        Cells number(int col, int style, String value) {
            xml.append("<c r=\"").append(ref(col)).append("\" s=\"").append(style)
                    .append("\"><v>").append(value).append("</v></c>");
            return this;
        }

        Cells formula(int col, int style, String formula) {
            xml.append("<c r=\"").append(ref(col)).append("\" s=\"").append(style)
                    .append("\"><f>").append(escape(formula)).append("</f></c>");
            return this;
        }
    }

    private static Cells cells(int row) {
        return new Cells(row);
    }

    private static void row(StringBuilder xml, int no, double height, Cells cells) {
        xml.append("<row r=\"").append(no).append("\" ht=\"").append(num(height))
                .append("\" customHeight=\"1\">").append(cells.xml).append("</row>");
    }

    /** 浮点 → 无多余尾零的文本（28.0 → "28"，12.25 → "12.25"），与样板存储形态一致。 */
    private static String num(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return Double.toString(value);
    }

    /* ---------------- 工具 ---------------- */

    /** LocalDate → Excel 日期序列（1900 纪元，含闰年修正）。 */
    private static String dateSerial(LocalDate date) {
        return String.valueOf(ChronoUnit.DAYS.between(EXCEL_EPOCH, date));
    }

    /** BigDecimal → 无多余尾零的十进制文本（11 → "11"，1.5 → "1.5"）。 */
    private static String number(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    /**
     * 工作表名清洗：Excel 限制 31 字符且不允许 []:*?/\。
     *
     * @param name 原名
     * @return 合法表名
     */
    public static String sheetName(String name) {
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

    private static byte[] readResource(String path) {
        try (InputStream in = StatementXlsxWriter.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("模板资源缺失：" + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        put(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }
}
