package com.example.admin.application.service.impl;

import com.example.admin.application.service.KdStatementAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.KdStatementRepository;
import com.example.admin.infrastructure.excel.XlsxWriter;
import com.example.admin.interfaces.dto.req.KdStatementQuery;
import com.example.admin.interfaces.dto.resp.KdStatementBillResp;
import com.example.admin.interfaces.dto.resp.KdStatementDetailResp;
import com.example.admin.interfaces.dto.resp.KdStatementHealthResp;
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
import java.util.Objects;

/**
 * 对账单应用服务实现。
 *
 * <p>口径（与桌面端本地对账单保持一致，便于双方核对）：
 * <ul>
 *     <li>只统计有效出库单（未作废、未被全量差集标失效）；</li>
 *     <li>金额以分录汇总为准，表头金额仅用于没有分录的兜底单据；</li>
 *     <li>按 {@code 客户 + 账套} 聚合：金蝶内码只在账套内唯一，跨账套合并会同名串号；</li>
 *     <li>默认窗口最近 90 天、单次最多 366 天，防止误操作全表扫描。</li>
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

    /** 导出明细分录上限（与既有导出保持一致，避免一次导出打爆内存） */
    private static final int EXPORT_MAX_LINES = 5000;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final KdStatementRepository statementRepository;
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
        // 客户编码：客户重名时靠编码识别（防串户），一次批量取回
        Map<String, String> customerNumbers = statementRepository.findCustomerNumbers(
                scope.ownerUserId(), bills);
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
        // 汇总行回填客户编码（与金额同 key：账套 + 客户内码）
        rows.forEach(r -> r.setCustomerNumber(customerNumbers.get(keyOf(r.getAcctId(),
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
        resp.setCustomerNumber(statementRepository.findCustomerNumbers(scope.ownerUserId(), bills)
                .get(keyOf(resp.getAcctId(), customerFid)));
        return resp;
    }

    @Override
    public byte[] export(KdStatementQuery query) {
        Scope scope = scope(query);
        Long customerFid = IdParser.parseIdOrNull(query.getCustomerFid());
        List<KdOutbound> bills = statementRepository.listBills(
                scope.ownerUserId(), scope.acctId(), scope.from(), scope.toExclusive(), customerFid);
        List<KdOutboundLine> lines = statementRepository.listLines(scope.ownerUserId(), bills);
        List<KdStatementBillResp> detailBills = buildBills(bills, lines);

        // 汇总按「账套 + 客户」聚合，导出时不受分页限制；
        // 金额取明细金额（分录口径），与页面上看到的一致
        Map<String, BigDecimal> amountByKey = new LinkedHashMap<>();
        Map<String, Integer> countByKey = new LinkedHashMap<>();
        Map<String, String> nameByKey = new LinkedHashMap<>();
        Map<String, String> acctByKey = new LinkedHashMap<>();
        // 客户编码：客户重名时靠编码识别（防串户）
        Map<String, String> numberByKey = statementRepository.findCustomerNumbers(
                scope.ownerUserId(), bills);
        for (int i = 0; i < bills.size(); i++) {
            KdOutbound bill = bills.get(i);
            String key = keyOf(bill.getAcctId(), bill.getCustomerFid());
            countByKey.merge(key, 1, Integer::sum);
            amountByKey.merge(key, detailBills.get(i).getAmount(), BigDecimal::add);
            nameByKey.putIfAbsent(key, bill.getCustomerName());
            acctByKey.putIfAbsent(key, bill.getAcctId());
        }

        List<String> summaryHeader = List.of("账套", "客户编码", "客户名称", "单据数", "金额合计");
        List<List<String>> summaryRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countByKey.entrySet()) {
            summaryRows.add(List.of(
                    nullToEmpty(acctByKey.get(entry.getKey())),
                    nullToEmpty(numberByKey.get(entry.getKey())),
                    nullToEmpty(nameByKey.get(entry.getKey())),
                    String.valueOf(entry.getValue()),
                    money(amountByKey.get(entry.getKey()))));
        }
        summaryRows.add(List.of("合计", "", "", String.valueOf(bills.size()),
                money(sumAmount(amountByKey, countByKey.keySet()))));

        List<String> detailHeader = List.of("客户编码", "客户名称", "出库单号", "业务日期", "物料编码", "物料名称",
                "规格型号", "单位", "数量", "单价", "金额", "源单编号", "客户PO", "备注");
        List<List<String>> detailRows = new ArrayList<>();
        // 先数清本期分录总数（截断后仍要知道"一共多少行"，不能边截断边数）
        int totalLines = 0;
        for (KdStatementBillResp bill : detailBills) {
            totalLines += bill.getLines().size();
        }
        boolean truncated = false;
        outer:
        for (int i = 0; i < detailBills.size(); i++) {
            KdStatementBillResp bill = detailBills.get(i);
            // buildBills 与 bills 保序，按下标回查「账套 + 客户内码」对应的编码
            String customerNumber = numberByKey.get(
                    keyOf(bills.get(i).getAcctId(), bills.get(i).getCustomerFid()));
            for (KdStatementLineResp line : bill.getLines()) {
                if (detailRows.size() >= EXPORT_MAX_LINES) {
                    // 对账单是给业务员和客户核数的依据，静默截断会让"导出文件"
                    // 与"页面 / 客户汇总"对不上且无人察觉；这里显式标记截断
                    truncated = true;
                    break outer;
                }
                detailRows.add(List.of(
                        nullToEmpty(customerNumber),
                        nullToEmpty(bill.getCustomerName()),
                        nullToEmpty(bill.getBillNo()),
                        nullToEmpty(bill.getBizDate()),
                        nullToEmpty(line.getMaterialNumber()),
                        nullToEmpty(line.getMaterialName()),
                        nullToEmpty(line.getSpec()),
                        nullToEmpty(line.getUnit()),
                        qty(line.getQty()),
                        money(line.getPrice()),
                        money(line.getAmount()),
                        nullToEmpty(line.getSrcOrderNo()),
                        nullToEmpty(line.getCustomerPo()),
                        nullToEmpty(line.getRemark())));
            }
        }
        if (detailRows.isEmpty()) {
            // 没有分录时导出单据级明细，避免"导出空表"
            for (int i = 0; i < detailBills.size(); i++) {
                KdStatementBillResp bill = detailBills.get(i);
                detailRows.add(List.of(
                        nullToEmpty(numberByKey.get(keyOf(bills.get(i).getAcctId(),
                                bills.get(i).getCustomerFid()))),
                        nullToEmpty(bill.getCustomerName()), nullToEmpty(bill.getBillNo()),
                        nullToEmpty(bill.getBizDate()), "", "", "", "", "", "",
                        money(bill.getAmount()), "", "", "（该单无分录数据）"));
            }
        } else if (truncated) {
            // 截断标记同时写进两个 sheet：客户汇总是完整口径、出库明细被截断，
            // 两边数字对不上时业务员能立刻看懂原因，并知道要缩小日期范围分批导出
            detailRows.add(List.of(String.format(
                    "（导出已达 %d 行上限，本期分录共 %d 行，明细未完；请缩小日期范围分批导出）",
                    EXPORT_MAX_LINES, totalLines)));
        }
        if (truncated) {
            summaryRows.add(List.of("说明", "", String.format(
                    "出库明细超出单次导出上限，仅含前 %d 行（本期分录共 %d 行）",
                    EXPORT_MAX_LINES, totalLines), "", ""));
        }

        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        sheets.add(XlsxWriter.sheet("客户汇总", summaryHeader, summaryRows));
        sheets.add(XlsxWriter.sheet("出库明细", detailHeader, detailRows));
        try {
            return XlsxWriter.write(sheets);
        } catch (IOException e) {
            throw new BusinessException("对账单导出失败：" + e.getMessage());
        }
    }

    @Override
    public KdStatementHealthResp health(KdStatementQuery query) {
        Scope scope = scope(query);
        // 体检口径与对账口径相反：作废 / 失效行必须可见（它们正是差额的来源）
        List<KdOutbound> all = statementRepository.listAllBillsIncludingExcluded(
                scope.ownerUserId(), scope.acctId(), scope.from(), scope.toExclusive());

        int billCount = 0;
        int cancelledCount = 0;
        int invalidCount = 0;
        List<KdOutbound> valid = new ArrayList<>(all.size());
        for (KdOutbound bill : all) {
            boolean cancelled = bill.getCancelled() != null && bill.getCancelled() == 1;
            boolean invalid = bill.getInvalid() != null && bill.getInvalid() == 1;
            if (invalid) {
                invalidCount++;
            } else if (cancelled) {
                cancelledCount++;
            } else {
                billCount++;
                valid.add(bill);
            }
        }

        // 无分录 = 有效单里没有一条有效分录：用「账套 + FID」关联（FID 只在账套内唯一）
        List<KdOutboundLine> lines = statementRepository.listLines(scope.ownerUserId(), valid);
        java.util.Set<String> withLines = new java.util.HashSet<>();
        for (KdOutboundLine line : lines) {
            withLines.add(billKey(line.getAcctId(), line.getOutboundFid()));
        }
        int noLineCount = 0;
        for (KdOutbound bill : valid) {
            if (!withLines.contains(billKey(bill.getAcctId(), bill.getKdFid()))) {
                noLineCount++;
            }
        }

        KdStatementHealthResp resp = new KdStatementHealthResp();
        resp.setDateFrom(scope.from().toLocalDate().format(DATE));
        resp.setDateTo(scope.toExclusive().toLocalDate().minusDays(1).format(DATE));
        resp.setBillCount(billCount);
        resp.setNoLineCount(noLineCount);
        resp.setCancelledCount(cancelledCount);
        resp.setInvalidCount(invalidCount);
        return resp;
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
    private BigDecimal derivedPrice(BigDecimal amount, BigDecimal qty) {
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

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static BigDecimal cents(Long value) {
        return BigDecimal.valueOf(value == null ? 0L : value, 2);
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal sumAmount(Map<String, BigDecimal> amountByKey, java.util.Set<String> keys) {
        return keys.stream().map(k -> Objects.requireNonNullElse(amountByKey.get(k), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
