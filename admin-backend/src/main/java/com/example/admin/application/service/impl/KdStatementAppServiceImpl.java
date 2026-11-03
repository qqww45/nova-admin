package com.example.admin.application.service.impl;

import com.example.admin.application.service.KdStatementAppService;
import com.example.admin.application.service.KdStatementExport;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.common.IdName;
import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.ConfigRepository;
import com.example.admin.domain.repository.KdStatementRepository;
import com.example.admin.infrastructure.excel.StatementXlsxWriter;
import com.example.admin.interfaces.dto.req.KdStatementQuery;
import com.example.admin.interfaces.dto.resp.KdStatementBillResp;
import com.example.admin.interfaces.dto.resp.KdStatementDetailResp;
import com.example.admin.interfaces.dto.resp.KdStatementLineResp;
import com.example.admin.interfaces.dto.resp.KdStatementSummaryResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对账单应用服务实现。
 *
 * <p>口径（与桌面端本地对账单保持一致，便于双方核对）：
 * <ul>
 *     <li>只统计有效出库单（未作废、未被全量差集标失效）；</li>
 *     <li>金额以分录汇总为准，表头金额仅用于没有分录的兜底单据；</li>
 *     <li>按 {@code 客户 + 账套} 聚合：金蝶内码只在账套内唯一，跨账套合并会同名串号；</li>
 *     <li>默认窗口最近 90 天、单次最多 366 天，防止误操作全表扫描；</li>
 *     <li>导出版式复刻金样板（见 {@link StatementXlsxWriter}）：客户 × 物料一个 sheet，
 *         金额列为「数量 × 单价」公式、页脚为 SUM 合计，与模板口径一致。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Service
@RequiredArgsConstructor
public class KdStatementAppServiceImpl implements KdStatementAppService {

    /** 未传日期时的默认窗口（天） */
    private static final int DEFAULT_WINDOW_DAYS = 90;

    /** 单次查询允许的最大窗口（天） */
    private static final int MAX_WINDOW_DAYS = 366;

    /**
     * 导出明细行上限。超出时直接报错而不是截断：对账单是给客户签字的文件，
     * 静默截断会让导出文件与页面 / 客户汇总对不上，必须让业务员主动缩小范围。
     */
    private static final int EXPORT_MAX_LINES = 5000;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /* 对账单抬头取系统配置，未配置时退回金样板文本 */
    private static final String CFG_COMPANY_NAME = "kd.statement.company.name";
    private static final String CFG_COMPANY_NAME_EN = "kd.statement.company.name-en";
    private static final String CFG_COMPANY_INFO = "kd.statement.company.info";
    private static final String DEFAULT_COMPANY_NAME = "深圳市一恒科电子科技有限公司";
    private static final String DEFAULT_COMPANY_NAME_EN = "SHENZHEN YIHENGK ELECTRONICS TECHNOLOGYCO.,LTD";
    private static final String DEFAULT_COMPANY_INFO =
            "地址：广东省深圳市南山区留仙大道创智云城A3栋A座8-9楼（成谷研发大厦）"
                    + "   电话：0755-26905055     传真：0755-26905050";

    private final KdStatementRepository statementRepository;
    private final ConfigRepository configRepository;
    private final BizUserRepository bizUserRepository;
    private final OperatorContext operatorContext;

    /**
     * 一次查询的隔离范围。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @param acctId 账套；null 表示不限
     * @param from 起（含）
     * @param toExclusive 止（不含，已经 +1 天）
     */
    private record Scope(Long ownerUserId, String acctId, LocalDateTime from, LocalDateTime toExclusive) {
    }

    @Override
    public PageResult<KdStatementSummaryResp> summary(KdStatementQuery query) {
        Scope scope = scope(query);
        List<KdOutbound> bills = statementRepository.listBills(
                scope.ownerUserId(), scope.acctId(), scope.from(), scope.toExclusive(), null);

        // key = acctId|kdFid：不同账套的客户内码会冲突
        Map<String, KdStatementSummaryResp> grouped = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        String keyword = trimToNull(query.getKeyword());

        for (KdOutbound bill : bills) {
            String key = keyOf(bill.getAcctId(), bill.getCustomerFid());
            KdStatementSummaryResp row = grouped.get(key);
            if (row == null) {
                row = new KdStatementSummaryResp();
                row.setAcctId(bill.getAcctId());
                row.setCustomerFid(bill.getCustomerFid() == null ? null : String.valueOf(bill.getCustomerFid()));
                row.setCustomerName(bill.getCustomerName());
                row.setBillCount(0);
                row.setFirstDate(dateOf(bill.getBizDate()));
                row.setLastDate(dateOf(bill.getBizDate()));
                grouped.put(key, row);
                amounts.put(key, BigDecimal.ZERO);
            }
            row.setBillCount(row.getBillCount() + 1);
            if (row.getCustomerName() == null || row.getCustomerName().isBlank()) {
                row.setCustomerName(bill.getCustomerName());
            }
            String date = dateOf(bill.getBizDate());
            if (date != null && (row.getFirstDate() == null || date.compareTo(row.getFirstDate()) < 0)) {
                row.setFirstDate(date);
            }
            if (date != null && (row.getLastDate() == null || date.compareTo(row.getLastDate()) > 0)) {
                row.setLastDate(date);
            }
            amounts.merge(key, cents(bill.getAmountCents()), BigDecimal::add);
        }

        List<KdStatementSummaryResp> rows = new ArrayList<>(grouped.values());
        rows.forEach(r -> r.setTotalAmount(amounts.get(keyOf(r.getAcctId(),
                IdParser.parseIdOrNull(r.getCustomerFid())))));
        if (keyword != null) {
            String lower = keyword.toLowerCase();
            rows.removeIf(r -> r.getCustomerName() == null
                    || !r.getCustomerName().toLowerCase().contains(lower));
        }
        rows.sort(Comparator.comparing(KdStatementSummaryResp::getTotalAmount,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long pageNum = query.getPageNum() == null ? 1 : Math.max(1, query.getPageNum());
        long pageSize = query.getPageSize() == null ? 10 : Math.max(1, query.getPageSize());
        int fromIndex = (int) Math.min((pageNum - 1) * pageSize, rows.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, rows.size());

        PageResult<KdStatementSummaryResp> result = new PageResult<>();
        result.setRecords(new ArrayList<>(rows.subList(fromIndex, toIndex)));
        result.setTotal(rows.size());
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages(pageSize == 0 ? 0 : (rows.size() + pageSize - 1) / pageSize);
        return result;
    }

    @Override
    public KdStatementDetailResp detail(KdStatementQuery query) {
        Scope scope = scope(query);
        Long customerFid = IdParser.parseIdOrNull(query.getCustomerFid());
        if (customerFid == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先指定客户");
        }
        List<KdOutbound> bills = statementRepository.listBills(
                scope.ownerUserId(), scope.acctId(), scope.from(), scope.toExclusive(), customerFid);
        // 金蝶客户内码只在账套内唯一：不指定账套时同一内码可能命中两个账套的两个不同客户，
        // 直接合并会让明细串户（金额对不上也查不出原因），必须显式拒绝
        long acctCount = bills.stream().map(KdOutbound::getAcctId)
                .filter(a -> a != null && !a.isBlank()).distinct().count();
        if (acctCount > 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "该客户内码在多个账套都有数据，请先指定账套（acctId）后重试");
        }
        List<KdOutboundLine> lines = statementRepository.listLines(scope.ownerUserId(), bills);

        KdStatementDetailResp resp = new KdStatementDetailResp();
        resp.setCustomerFid(String.valueOf(customerFid));
        resp.setDateFrom(scope.from().toLocalDate().format(DATE));
        resp.setDateTo(scope.toExclusive().toLocalDate().minusDays(1).format(DATE));
        resp.setBills(buildBills(bills, lines));

        BigDecimal total = BigDecimal.ZERO;
        int lineCount = 0;
        for (KdStatementBillResp bill : resp.getBills()) {
            total = total.add(bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount());
            lineCount += bill.getLines().size();
        }
        resp.setBillCount(bills.size());
        resp.setLineCount(lineCount);
        resp.setTotalAmount(total);
        resp.setAcctId(bills.isEmpty() ? scope.acctId() : bills.get(0).getAcctId());
        resp.setCustomerName(bills.isEmpty() ? null : bills.get(0).getCustomerName());
        return resp;
    }

    @Override
    public KdStatementExport export(KdStatementQuery query) {
        Scope scope = scope(query);
        Long customerFid = IdParser.parseIdOrNull(query.getCustomerFid());
        List<KdOutbound> bills = statementRepository.listBills(
                scope.ownerUserId(), scope.acctId(), scope.from(), scope.toExclusive(), customerFid);
        if (bills.isEmpty()) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND,
                    "所选范围内没有可对账的出库数据，请调整客户或日期区间后重试");
        }
        List<KdOutboundLine> lines = statementRepository.listLines(scope.ownerUserId(), bills);
        Map<String, List<KdOutboundLine>> linesByBill = new LinkedHashMap<>();
        for (KdOutboundLine line : lines) {
            linesByBill.computeIfAbsent(billKey(line.getAcctId(), line.getOutboundFid()),
                    k -> new ArrayList<>()).add(line);
        }

        // 1) 组装「客户 × 物料」分组（顺序 = 单据日期序，组内再按物料首现顺序拆 sheet）
        Map<String, CustomerGroup> groups = new LinkedHashMap<>();
        int rowCount = 0;
        for (KdOutbound bill : bills) {
            String key = keyOf(bill.getAcctId(), bill.getCustomerFid());
            CustomerGroup group = groups.computeIfAbsent(key,
                    k -> new CustomerGroup(bill.getAcctId(), bill.getCustomerFid()));
            if (group.customerName == null || group.customerName.isBlank()) {
                group.customerName = bill.getCustomerName();
            }
            group.ownerUserId = bill.getOwnerUserId();
            List<KdOutboundLine> billLines = linesByBill.getOrDefault(
                    billKey(bill.getAcctId(), bill.getKdFid()), List.of());
            if (billLines.isEmpty()) {
                // 无分录单据（权限只拉到表头）：退回表头金额并标注，不能不进对账
                group.rows.add(new StatementXlsxWriter.Row(
                        bill.getBizDate() == null ? null : bill.getBizDate().toLocalDate(),
                        bill.getBillNo(), null, null, null, null, null,
                        null, null, cents(bill.getAmountCents()), "（该单无分录数据）"));
                rowCount++;
            } else {
                for (KdOutboundLine line : billLines) {
                    BigDecimal amount = cents(line.getAmountCents());
                    BigDecimal qty = line.getQtyMilli() == null ? null
                            : BigDecimal.valueOf(line.getQtyMilli(), 3);
                    group.rows.add(new StatementXlsxWriter.Row(
                            bill.getBizDate() == null ? null : bill.getBizDate().toLocalDate(),
                            bill.getBillNo(), line.getCustomerPo(),
                            line.getMaterialNumber(), line.getMaterialName(), line.getSpec(),
                            line.getUnit(), qty,
                            priceOf(line, amount, qty), null, line.getRemark()));
                    rowCount++;
                }
            }
        }

        // 2) 关键字口径与汇总页一致（客户名称包含），避免「页面筛选了、导出没筛」
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            String lower = keyword.toLowerCase();
            groups.values().removeIf(g -> g.customerName == null
                    || !g.customerName.toLowerCase().contains(lower));
        }
        if (groups.isEmpty()) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND,
                    "所选范围内没有可对账的出库数据，请调整客户或日期区间后重试");
        }

        // 3) 行数上限：超限显式报错，绝不静默截断对账文件
        if (rowCount > EXPORT_MAX_LINES) {
            throw new BusinessException(ResultCode.PARAM_ERROR, String.format(
                    "本次导出共 %d 行明细，超过单次上限 %d 行；请缩小日期范围或指定客户分批导出",
                    rowCount, EXPORT_MAX_LINES));
        }

        // 4) 抬头与落款信息
        String companyName = configValue(CFG_COMPANY_NAME, DEFAULT_COMPANY_NAME);
        String companyEnName = configValue(CFG_COMPANY_NAME_EN, DEFAULT_COMPANY_NAME_EN);
        String companyInfo = configValue(CFG_COMPANY_INFO, DEFAULT_COMPANY_INFO);
        LocalDate statementDate = LocalDate.now();
        LoginUser loginUser = operatorContext.current();
        String preparer = loginUser == null ? null : loginUser.getNickname();
        Map<Long, String> ownerNames = ownerNames(groups.values());

        List<StatementXlsxWriter.Sheet> sheets = new ArrayList<>();
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        for (CustomerGroup group : groups.values()) {
            KdCustomer customer = statementRepository.findCustomer(
                    scope.ownerUserId(), group.acctId, group.customerFid);
            // 客户 × 物料一个 sheet（金样板即按产品分页）；单物料时就是样板原样
            Map<String, List<StatementXlsxWriter.Row>> byMaterial = new LinkedHashMap<>();
            for (StatementXlsxWriter.Row row : group.rows) {
                byMaterial.computeIfAbsent(
                        row.materialNumber() == null || row.materialNumber().isBlank()
                                ? "" : row.materialNumber(),
                        k -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<String, List<StatementXlsxWriter.Row>> entry : byMaterial.entrySet()) {
                String material = entry.getKey();
                String base = material.isBlank() ? "汇总" : StatementXlsxWriter.sheetName(material);
                StatementXlsxWriter.Header header = new StatementXlsxWriter.Header(
                        companyName, companyEnName, companyInfo,
                        group.customerName, null, null,
                        customer == null ? null : customer.getAddress(),
                        statementDate);
                sheets.add(new StatementXlsxWriter.Sheet(
                        uniqueName(usedNames, base),
                        header,
                        material.isBlank() ? "" : material,
                        preparer, ownerNames.get(group.ownerUserId),
                        entry.getValue()));
            }
        }

        try {
            String period = scope.from().toLocalDate().format(DATE) + "_"
                    + scope.toExclusive().toLocalDate().minusDays(1).format(DATE);
            String fileName = groups.size() == 1
                    ? "对账单-" + sanitizeFileName(groups.values().iterator().next().customerName)
                            + "-" + period + ".xlsx"
                    : "金蝶对账单-" + period + ".xlsx";
            return new KdStatementExport(fileName, StatementXlsxWriter.write(sheets));
        } catch (IOException e) {
            throw new BusinessException("对账单导出失败：" + e.getMessage());
        }
    }

    /** 客户分组（导出中间结构，key = 账套 | 客户内码）。 */
    private static final class CustomerGroup {
        private final String acctId;
        private final Long customerFid;
        private String customerName;
        private Long ownerUserId;
        private final List<StatementXlsxWriter.Row> rows = new ArrayList<>();

        private CustomerGroup(String acctId, Long customerFid) {
            this.acctId = acctId;
            this.customerFid = customerFid;
        }
    }

    /**
     * 单价：优先金蝶含税单价；缺失时按金额 / 数量反推（4 位精度，与明细页口径一致）。
     *
     * @param line   分录
     * @param amount 金额
     * @param qty    数量
     * @return 单价
     */
    private static BigDecimal priceOf(KdOutboundLine line, BigDecimal amount, BigDecimal qty) {
        if (line.getPriceCents() != null && line.getPriceCents() != 0) {
            return cents(line.getPriceCents());
        }
        return derivedPrice(amount, qty);
    }

    /**
     * 取系统配置，缺失或空值时退回金样板默认文本。
     *
     * @param key      配置键
     * @param fallback 默认值
     * @return 配置值
     */
    private String configValue(String key, String fallback) {
        return configRepository.findByKey(key)
                .map(ConfigItem::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }

    /** 归属业务员 ID → 姓名（导出落款「业务：」用）。 */
    private Map<Long, String> ownerNames(Iterable<CustomerGroup> groups) {
        List<Long> ids = new ArrayList<>();
        for (CustomerGroup group : groups) {
            if (group.ownerUserId != null && !ids.contains(group.ownerUserId)) {
                ids.add(group.ownerUserId);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bizUserRepository.findNames(ids).stream()
                .collect(Collectors.toMap(IdName::id, IdName::name, (a, b) -> a));
    }

    /** sheet 重名时追加序号，保证工作簿内唯一（多客户同物料时会出现）。 */
    private static String uniqueName(Map<String, Integer> used, String base) {
        int seq = used.merge(base, 1, Integer::sum);
        if (seq == 1) {
            return base;
        }
        String suffix = "(" + seq + ")";
        return base.length() + suffix.length() > 31
                ? base.substring(0, 31 - suffix.length()) + suffix
                : base + suffix;
    }

    /** 文件名里的客户名清洗（去掉 Windows / 浏览器都不欢迎的字符）。 */
    private static String sanitizeFileName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\s]", "");
        return cleaned.isBlank() ? "客户" : cleaned;
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 把单据 + 分录组装成明细行，金额以分录为准。
     *
     * @param bills 单据
     * @param lines 分录（可能为空）
     * @return 明细
     */
    private List<KdStatementBillResp> buildBills(List<KdOutbound> bills, List<KdOutboundLine> lines) {
        // 关联键必须是「账套 + FID」：金蝶 FID 只在账套内唯一
        Map<String, List<KdOutboundLine>> byBill = new LinkedHashMap<>();
        for (KdOutboundLine line : lines) {
            byBill.computeIfAbsent(billKey(line.getAcctId(), line.getOutboundFid()),
                    k -> new ArrayList<>()).add(line);
        }
        List<KdStatementBillResp> result = new ArrayList<>(bills.size());
        for (KdOutbound bill : bills) {
            List<KdOutboundLine> billLines =
                    byBill.getOrDefault(billKey(bill.getAcctId(), bill.getKdFid()), List.of());
            KdStatementBillResp row = new KdStatementBillResp();
            row.setCustomerName(bill.getCustomerName());
            row.setBillNo(bill.getBillNo());
            row.setBizDate(dateOf(bill.getBizDate()));
            row.setDocStatus(bill.getDocStatus());
            BigDecimal amount = BigDecimal.ZERO;
            for (KdOutboundLine line : billLines) {
                KdStatementLineResp lineResp = new KdStatementLineResp();
                lineResp.setEntryId(line.getEntryId() == null ? null : String.valueOf(line.getEntryId()));
                lineResp.setMaterialNumber(line.getMaterialNumber());
                lineResp.setMaterialName(line.getMaterialName());
                lineResp.setSpec(line.getSpec());
                lineResp.setUnit(line.getUnit());
                lineResp.setSrcOrderNo(line.getSrcOrderNo());
                lineResp.setCustomerPo(line.getCustomerPo());
                lineResp.setRemark(line.getRemark());
                // 整数存储还原：数量 milli(×1000)，金额/单价 cents(×100)
                lineResp.setQty(line.getQtyMilli() == null ? BigDecimal.ZERO
                        : BigDecimal.valueOf(line.getQtyMilli(), 3));
                lineResp.setAmount(cents(line.getAmountCents()));
                lineResp.setPrice(line.getPriceCents() != null && line.getPriceCents() != 0
                        ? cents(line.getPriceCents())
                        : derivedPrice(lineResp.getAmount(), lineResp.getQty()));
                row.getLines().add(lineResp);
                amount = amount.add(lineResp.getAmount());
            }
            // 没有分录的单据（如权限不足只拉到表头）退回表头金额
            row.setAmount(billLines.isEmpty() ? cents(bill.getAmountCents()) : amount);
            result.add(row);
        }
        return result;
    }

    /**
     * 单价缺失时由金额 / 数量反推（金蝶部分字段集拿不到 FTaxPrice）。
     *
     * @param amount 金额
     * @param qty 数量
     * @return 单价，数量为 0 时返回 0
     */
    private static BigDecimal derivedPrice(BigDecimal amount, BigDecimal qty) {
        if (amount == null || qty == null || qty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(qty, 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 解析查询范围并施加数据隔离。
     *
     * @param query 查询条件
     * @return 范围
     */
    private Scope scope(KdStatementQuery query) {
        LoginUser loginUser = operatorContext.current();
        Long ownerUserId = isAdmin(loginUser) ? null : operatorContext.currentUserId();

        LocalDate to = parseDate(query.getDateTo());
        LocalDate from = parseDate(query.getDateFrom());
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        }
        if (from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        if (from.plusDays(MAX_WINDOW_DAYS).isBefore(to)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "查询区间不能超过 " + MAX_WINDOW_DAYS + " 天");
        }
        return new Scope(ownerUserId, trimToNull(query.getAcctId()),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    /**
     * 判断是否超管（超管可跨业务员查看）。
     *
     * @param loginUser 登录人
     * @return 是否超管
     */
    private boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }

    private static String keyOf(String acctId, Long customerFid) {
        return (acctId == null ? "" : acctId) + "|" + (customerFid == null ? 0L : customerFid);
    }

    /**
     * 单据关联键：账套 + FID（金蝶 FID 只在账套内唯一）。
     *
     * @param acctId 账套
     * @param fid 单据 FID
     * @return 关联键
     */
    private static String billKey(String acctId, Long fid) {
        return (acctId == null ? "" : acctId) + "#" + (fid == null ? 0L : fid);
    }

    private static LocalDate parseDate(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value, DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd：" + text);
        }
    }

    private static String dateOf(LocalDateTime time) {
        return time == null ? null : time.toLocalDate().format(DATE);
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal cents(Long value) {
        return BigDecimal.valueOf(value == null ? 0L : value, 2);
    }
}
