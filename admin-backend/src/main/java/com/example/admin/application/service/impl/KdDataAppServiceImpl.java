package com.example.admin.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.service.KdDataAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.common.IdName;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdCustomerBrowseQuery;
import com.example.admin.domain.model.kd.KdLineBrowseQuery;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundBrowseQuery;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdDataRepository;
import com.example.admin.interfaces.dto.req.KdCustomerPageQuery;
import com.example.admin.interfaces.dto.req.KdLinePageQuery;
import com.example.admin.interfaces.dto.req.KdOutboundPageQuery;
import com.example.admin.interfaces.dto.resp.KdCustomerDataResp;
import com.example.admin.interfaces.dto.resp.KdOutboundDataResp;
import com.example.admin.interfaces.dto.resp.KdOutboundLineDataResp;
import com.example.admin.interfaces.dto.resp.KdOwnerResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 金蝶同步数据浏览应用服务实现。
 *
 * <p>隔离口径（与 KdStatementAppServiceImpl / KdSyncAppServiceImpl 保持一致）：
 * 非超管强制 ownerUserId = 当前登录用户；超管为 null（看全部）
 * 或显式指定某个业务员（按人排查）。
 *
 * <p>排查口径（与对账单的差异）：不过滤作废 / 失效——它们本身就是
 * "对账单少数据"的常见根因，必须在浏览页可见（docs/金蝶对账同步方案.md §3.3）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class KdDataAppServiceImpl implements KdDataAppService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 金蝶 FDocumentStatus → 标签（V15 注释同源） */
    private static final Map<String, String> DOC_STATUS_LABELS = Map.of(
            "A", "创建", "B", "审核中", "C", "已审核", "D", "重新审核");

    private final KdDataRepository dataRepository;
    private final KdBindingRepository bindingRepository;
    private final BizUserRepository bizUserRepository;
    private final OperatorContext operatorContext;

    @Override
    public PageResult<KdCustomerDataResp> customerPage(KdCustomerPageQuery query) {
        KdCustomerBrowseQuery domain = new KdCustomerBrowseQuery();
        copyPage(query, domain);
        domain.setAcctId(trimToNull(query.getAcctId()));
        domain.setKeyword(trimToNull(query.getKeyword()));
        domain.setDisabled(query.getDisabled());
        domain.setBatchNo(trimToNull(query.getBatchNo()));
        domain.setSyncFrom(dayStart(query.getSyncDateFrom()));
        domain.setSyncTo(dayEndExclusive(query.getSyncDateTo()));
        domain.setOwnerUserId(scopeOwner(query.getOwnerUserId()));

        PageResult<KdCustomer> page = dataRepository.pageCustomers(domain);
        Map<Long, String> ownerNames = loadUserNames(page.getRecords().stream()
                .map(KdCustomer::getOwnerUserId).toList());

        PageResult<KdCustomerDataResp> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords().stream()
                .map(customer -> toCustomerResp(customer, ownerNames)).toList());
        return result;
    }

    @Override
    public PageResult<KdOutboundDataResp> outboundPage(KdOutboundPageQuery query) {
        KdOutboundBrowseQuery domain = new KdOutboundBrowseQuery();
        copyPage(query, domain);
        domain.setAcctId(trimToNull(query.getAcctId()));
        domain.setKeyword(trimToNull(query.getKeyword()));
        domain.setBizFrom(dayStart(query.getDateFrom()));
        domain.setBizTo(dayEndExclusive(query.getDateTo()));
        domain.setDocStatus(trimToNull(query.getDocStatus()));
        domain.setCancelled(query.getCancelled());
        domain.setInvalid(query.getInvalid());
        domain.setBatchNo(trimToNull(query.getBatchNo()));
        domain.setOwnerUserId(scopeOwner(query.getOwnerUserId()));

        PageResult<KdOutbound> page = dataRepository.pageOutbounds(domain);
        List<KdOutbound> bills = page.getRecords();
        Map<Long, String> ownerNames = loadUserNames(bills.stream()
                .map(KdOutbound::getOwnerUserId).toList());
        // 分录行数：0 行 = "该单无分录数据"（对账单退回表头金额的标志），排查第一线索
        Map<String, Integer> lineCounts = dataRepository.countLinesByBill(
                domain.getOwnerUserId(), fidsByAcct(bills));

        PageResult<KdOutboundDataResp> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        result.setRecords(bills.stream()
                .map(bill -> toOutboundResp(bill, ownerNames, lineCounts)).toList());
        return result;
    }

    @Override
    public List<KdOutboundLineDataResp> outboundLines(String id) {
        KdOutbound bill = dataRepository.findBillById(IdParser.parseId(id));
        if (bill == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "出库单不存在");
        }
        Long scope = scopeOwner(null);
        if (scope != null && !scope.equals(bill.getOwnerUserId())) {
            // 返回"不存在"而不是"无权限"：不暴露他人数据是否存在（与绑定管理一致）
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "出库单不存在");
        }
        List<KdOutboundLine> lines = dataRepository.listLinesOfBill(
                scope, bill.getAcctId(), bill.getKdFid());
        return lines.stream().map(this::toLineResp).toList();
    }

    @Override
    public PageResult<KdOutboundLineDataResp> linePage(KdLinePageQuery query) {
        KdLineBrowseQuery domain = new KdLineBrowseQuery();
        copyPage(query, domain);
        domain.setAcctId(trimToNull(query.getAcctId()));
        domain.setOutboundFid(IdParser.parseIdOrNull(query.getOutboundFid()));
        domain.setKeyword(trimToNull(query.getKeyword()));
        domain.setInvalid(query.getInvalid());
        domain.setBatchNo(trimToNull(query.getBatchNo()));
        domain.setSyncFrom(dayStart(query.getSyncDateFrom()));
        domain.setSyncTo(dayEndExclusive(query.getSyncDateTo()));
        domain.setOwnerUserId(scopeOwner(query.getOwnerUserId()));

        PageResult<KdOutboundLine> page = dataRepository.pageLines(domain);
        List<KdOutboundLine> lines = page.getRecords();
        Map<Long, String> ownerNames = loadUserNames(lines.stream()
                .map(KdOutboundLine::getOwnerUserId).toList());
        // 回填单据编号：分录表没有 bill_no，按「账套 + FID」取表头（FID 只在账套内唯一）
        Map<Long, List<KdOutbound>> billsByFid = dataRepository.findBillsByFids(
                        domain.getOwnerUserId(), fidsByAcctOfLines(lines)).stream()
                .collect(Collectors.groupingBy(KdOutbound::getKdFid));

        PageResult<KdOutboundLineDataResp> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        result.setRecords(lines.stream().map(line -> {
            KdOutboundLineDataResp resp = toLineResp(line);
            resp.setOwnerUserName(ownerNames.get(line.getOwnerUserId()));
            // 同账套同 FID 才是同一张单；取不到表头（如只同步了分录）显示 FID 兜底
            billsByFid.getOrDefault(line.getOutboundFid(), List.of()).stream()
                    .filter(bill -> StrUtil.equals(bill.getAcctId(), line.getAcctId()))
                    .findFirst()
                    .ifPresent(bill -> resp.setBillNo(bill.getBillNo()));
            return resp;
        }).toList());
        return result;
    }

    @Override
    public List<KdOwnerResp> owners() {
        requireAdmin();
        // 有绑定才有数据：归属人下拉以 kd_binding 为准，避免列出全量系统用户
        List<Long> userIds = bindingRepository.listByUser(null).stream()
                .map(com.example.admin.domain.model.kd.KdBinding::getUserId)
                .distinct().toList();
        List<KdOwnerResp> result = new ArrayList<>();
        for (IdName name : bizUserRepository.findNames(userIds)) {
            KdOwnerResp resp = new KdOwnerResp();
            resp.setId(String.valueOf(name.id()));
            resp.setName(name.name());
            result.add(resp);
        }
        return result;
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 解析隔离口径：非超管强制本人（忽略入参），超管可指定或看全部。
     *
     * @param ownerUserIdParam 查询参数里的归属人（仅管理员生效）
     * @return 生效的 ownerUserId；null = 不限（仅管理员）
     */
    private Long scopeOwner(String ownerUserIdParam) {
        LoginUser loginUser = operatorContext.current();
        if (isAdmin(loginUser)) {
            return IdParser.parseIdOrNull(ownerUserIdParam);
        }
        return operatorContext.currentUserId();
    }

    /**
     * 断言当前登录人是超管（owners 下拉只给管理员）。
     */
    private void requireAdmin() {
        if (!isAdmin(operatorContext.current())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可查看业务员列表");
        }
    }

    /**
     * 是否超级管理员。
     *
     * @param loginUser 登录人
     * @return 是否管理员
     */
    private boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }

    /**
     * 批量取用户姓名（找不到的 ID 不出现在结果里）。
     *
     * @param userIds 用户 ID 列表
     * @return 用户 ID → 姓名
     */
    private Map<Long, String> loadUserNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bizUserRepository.findNames(ids).stream()
                .collect(Collectors.toMap(IdName::id, IdName::name, (a, b) -> a));
    }

    /** 分页参数拷贝（领域查询与 DTO 都继承 PageQuery） */
    private void copyPage(com.example.admin.common.page.PageQuery from,
                          com.example.admin.common.page.PageQuery to) {
        to.setPageNum(from.getPageNum());
        to.setPageSize(from.getPageSize());
        to.setSortBy(from.getSortBy());
        to.setSortOrder(from.getSortOrder());
    }

    /** 出库单列表按账套归组的 FID 映射（金蝶 FID 只在账套内唯一） */
    private Map<String, List<Long>> fidsByAcct(List<KdOutbound> bills) {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        for (KdOutbound bill : bills) {
            String acct = bill.getAcctId() == null ? "" : bill.getAcctId();
            result.computeIfAbsent(acct, k -> new ArrayList<>()).add(bill.getKdFid());
        }
        return result;
    }

    /** 分录列表按账套归组的 FID 映射 */
    private Map<String, List<Long>> fidsByAcctOfLines(List<KdOutboundLine> lines) {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        for (KdOutboundLine line : lines) {
            String acct = line.getAcctId() == null ? "" : line.getAcctId();
            result.computeIfAbsent(acct, k -> new ArrayList<>()).add(line.getOutboundFid());
        }
        return result;
    }

    /** 客户 → 响应 */
    private KdCustomerDataResp toCustomerResp(KdCustomer customer, Map<Long, String> ownerNames) {
        KdCustomerDataResp resp = new KdCustomerDataResp();
        resp.setId(str(customer.getId()));
        resp.setOwnerUserId(str(customer.getOwnerUserId()));
        resp.setOwnerUserName(ownerNames.get(customer.getOwnerUserId()));
        resp.setAcctId(customer.getAcctId());
        resp.setKdFid(str(customer.getKdFid()));
        resp.setKdNumber(customer.getKdNumber());
        resp.setCustomerName(customer.getCustomerName());
        resp.setAddress(customer.getAddress());
        resp.setDisabled(customer.getDisabled());
        resp.setBatchNo(customer.getBatchNo());
        resp.setSyncAt(customer.getSyncAt());
        return resp;
    }

    /** 出库单 → 响应 */
    private KdOutboundDataResp toOutboundResp(KdOutbound bill, Map<Long, String> ownerNames,
                                              Map<String, Integer> lineCounts) {
        KdOutboundDataResp resp = new KdOutboundDataResp();
        resp.setId(str(bill.getId()));
        resp.setOwnerUserId(str(bill.getOwnerUserId()));
        resp.setOwnerUserName(ownerNames.get(bill.getOwnerUserId()));
        resp.setAcctId(bill.getAcctId());
        resp.setKdFid(str(bill.getKdFid()));
        resp.setBillNo(bill.getBillNo());
        resp.setBizDate(bill.getBizDate());
        resp.setCustomerFid(str(bill.getCustomerFid()));
        resp.setCustomerName(bill.getCustomerName());
        resp.setDocStatus(bill.getDocStatus());
        resp.setDocStatusLabel(docStatusLabel(bill.getDocStatus()));
        resp.setCancelStatus(bill.getCancelStatus());
        resp.setCancelled(bill.getCancelled());
        resp.setAmount(cents(bill.getAmountCents()));
        resp.setLineCount(lineCounts.get(billKey(bill.getAcctId(), bill.getKdFid())));
        resp.setRemark(bill.getRemark());
        resp.setKdModifyTime(bill.getKdModifyTime());
        resp.setInvalid(bill.getInvalid());
        resp.setBatchNo(bill.getBatchNo());
        resp.setSyncAt(bill.getSyncAt());
        return resp;
    }

    /** 分录 → 响应（不含单据编号，跨单检索时由调用方回填） */
    private KdOutboundLineDataResp toLineResp(KdOutboundLine line) {
        KdOutboundLineDataResp resp = new KdOutboundLineDataResp();
        resp.setId(str(line.getId()));
        resp.setOwnerUserId(str(line.getOwnerUserId()));
        resp.setAcctId(line.getAcctId());
        resp.setOutboundFid(str(line.getOutboundFid()));
        resp.setEntryId(str(line.getEntryId()));
        resp.setMaterialNumber(line.getMaterialNumber());
        resp.setMaterialName(line.getMaterialName());
        resp.setSpec(line.getSpec());
        resp.setUnit(line.getUnit());
        // 整数存储还原：数量 milli(×1000)，金额/单价 cents(×100)
        resp.setQty(line.getQtyMilli() == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(line.getQtyMilli(), 3));
        resp.setAmount(cents(line.getAmountCents()));
        resp.setPrice(line.getPriceCents() != null && line.getPriceCents() != 0
                ? cents(line.getPriceCents())
                : derivedPrice(resp.getAmount(), resp.getQty()));
        resp.setSrcOrderNo(line.getSrcOrderNo());
        resp.setCustomerPo(line.getCustomerPo());
        resp.setRemark(line.getRemark());
        resp.setInvalid(line.getInvalid());
        resp.setBatchNo(line.getBatchNo());
        resp.setSyncAt(line.getSyncAt());
        return resp;
    }

    /** 单价缺失时由金额 / 数量反推（与对账单同口径） */
    private BigDecimal derivedPrice(BigDecimal amount, BigDecimal qty) {
        if (amount == null || qty == null || qty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(qty, 4, java.math.RoundingMode.HALF_UP);
    }

    /** FDocumentStatus → 中文标签；未知值原样返回 */
    private String docStatusLabel(String docStatus) {
        if (docStatus == null) {
            return null;
        }
        return DOC_STATUS_LABELS.getOrDefault(docStatus.toUpperCase(), docStatus);
    }

    /** Long → String（前端 ID 精度保护） */
    private String str(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 分 → 元（2 位小数） */
    private BigDecimal cents(Long value) {
        return BigDecimal.valueOf(value == null ? 0L : value, 2);
    }

    /** 单据关联键：账套 + FID */
    private String billKey(String acctId, Long fid) {
        return (acctId == null ? "" : acctId) + "#" + (fid == null ? 0L : fid);
    }

    /** yyyy-MM-dd → 当天 00:00；空返回 null */
    private LocalDateTime dayStart(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.atStartOfDay();
    }

    /** yyyy-MM-dd → 次日 00:00（查询用「含当天」语义，条件是小于该值）；空返回 null */
    private LocalDateTime dayEndExclusive(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.plusDays(1).atStartOfDay();
    }

    /** 宽容解析：超长输入（带时间）截断到日期部分 */
    private LocalDate parseDate(String text) {
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

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
