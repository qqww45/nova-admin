package com.example.admin.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.service.StatementAppService;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.reconciliation.CustomerOpening;
import com.example.admin.domain.model.reconciliation.Statement;
import com.example.admin.domain.model.reconciliation.StatementLine;
import com.example.admin.domain.model.reconciliation.StatementQuery;
import com.example.admin.domain.model.reconciliation.StatementLineType;
import com.example.admin.domain.model.reconciliation.StatementStatus;
import com.example.admin.domain.model.receipt.Receipt;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.model.shipment.ShipmentType;
import com.example.admin.domain.repository.CustomerOpeningRepository;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.OperationLogRepository;
import com.example.admin.domain.repository.ReceiptRepository;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.domain.repository.StatementRepository;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.service.StatementGenerator;
import com.example.admin.infrastructure.excel.XlsxWriter;
import com.example.admin.interfaces.dto.req.StatementGenerateReq;
import com.example.admin.interfaces.dto.req.StatementPageQuery;
import com.example.admin.interfaces.dto.req.StatementPreviewReq;
import com.example.admin.interfaces.dto.resp.StatementLineResp;
import com.example.admin.interfaces.dto.resp.StatementPreviewResp;
import com.example.admin.interfaces.dto.resp.StatementResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 对账单应用服务实现。
 *
 * <p>核心聚合交给 {@link StatementGenerator}（纯函数、可单测），本类负责：取数 → 转事实 → 调聚合 →
 * 落库快照 → 操作日志；以及签发/确认/差异的状态流转与 Excel 导出。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatementAppServiceImpl implements StatementAppService {

    private final StatementRepository statementRepository;
    private final ReceiptRepository receiptRepository;
    private final ShipmentRepository shipmentRepository;
    private final CustomerOpeningRepository openingRepository;
    private final CustomerRepository customerRepository;
    private final OperationLogRepository operationLogRepository;
    private final OperatorContext operatorContext;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public PageResult<StatementResp> page(StatementPageQuery query) {
        StatementQuery dq = new StatementQuery();
        dq.setKeyword(query.getKeyword());
        dq.setCustomerId(query.getCustomerId());
        dq.setStatus(query.getStatus());
        dq.setBeginDate(query.getBeginDate());
        dq.setEndDate(query.getEndDate());
        dq.setPageNum(query.getPageNum());
        dq.setPageSize(query.getPageSize());
        dq.setSortBy(query.getSortBy());
        dq.setSortOrder(query.getSortOrder());
        PageResult<Statement> page = statementRepository.page(dq);
        List<StatementResp> records = new ArrayList<>();
        for (Statement statement : page.getRecords()) {
            records.add(toResp(statement, null));
        }
        PageResult<StatementResp> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        return result;
    }

    @Override
    public StatementResp detail(String id) {
        Statement statement = statementRepository.findById(IdParser.parseId(id));
        if (statement == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "对账单不存在");
        }
        List<StatementLine> lines = statementRepository.findLines(statement.getId());
        return toResp(statement, lines);
    }

    @Override
    public StatementPreviewResp preview(StatementPreviewReq req) {
        StatementGenerator.StatementResult result = aggregate(IdParser.parseId(req.getCustomerId()),
                req.getStartDate(), req.getEndDate());
        StatementPreviewResp resp = new StatementPreviewResp();
        resp.setLines(toLineRespFromGen(result.lines()));
        resp.setOpeningBalance(result.summary().openingBalance());
        resp.setShippedAmount(result.summary().shippedAmount());
        resp.setReturnedAmount(result.summary().returnedAmount());
        resp.setReceivedAmount(result.summary().receivedAmount());
        resp.setEndingBalance(result.summary().endingBalance());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generate(StatementGenerateReq req) {
        Long customerId = IdParser.parseId(req.getCustomerId());
        if (customerRepository.findById(customerId) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在：" + req.getCustomerId());
        }
        Long operatorId = operatorContext.currentUserIdOrNull();
        StatementGenerator.StatementResult result = aggregate(customerId, req.getStartDate(), req.getEndDate());

        Statement statement = new Statement();
        statement.setStmtNo(nextStmtNo());
        statement.setCustomerId(customerId);
        statement.setStartDate(req.getStartDate());
        statement.setEndDate(req.getEndDate());
        statement.setOpeningBalance(result.summary().openingBalance());
        statement.setShippedAmount(result.summary().shippedAmount());
        statement.setReturnedAmount(result.summary().returnedAmount());
        statement.setReceivedAmount(result.summary().receivedAmount());
        statement.setEndingBalance(result.summary().endingBalance());
        statement.setStatus(StatementStatus.DRAFT);
        statement.setRemark(req.getRemark());
        statement.setCreateBy(operatorId);
        statement.setUpdateBy(operatorId);
        Long stmtId = statementRepository.save(statement);

        for (StatementGenerator.StatementLine line : result.lines()) {
            StatementLine domain = new StatementLine();
            domain.setStatementId(stmtId);
            domain.setLineType(line.lineType());
            domain.setBizDate(line.bizDate());
            domain.setDocNo(line.docNo());
            domain.setCustomerOrderNo(line.customerOrderNo());
            domain.setMaterialNo(line.materialNo());
            domain.setMaterialName(line.materialName());
            domain.setSpec(line.spec());
            domain.setUnit(line.unit());
            domain.setQty(line.qty());
            domain.setUnitPrice(line.unitPrice());
            domain.setAmount(line.amount());
            domain.setRemark(line.remark());
            domain.setSourceId(line.sourceId());
            domain.setSortNo(line.sortNo());
            statementRepository.saveLine(domain);
        }
        operationLogRepository.save(OperationLog.of("STATEMENT", String.valueOf(stmtId), "CREATE",
                null, "生成对账单 " + statement.getStmtNo(), null, operatorId, LocalDateTime.now()));
        return String.valueOf(stmtId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(String id) {
        Statement statement = load(id);
        if (!statement.isDraft()) {
            throw new BusinessException("仅草稿状态可对账单签发");
        }
        statement.issue();
        statement.setUpdateBy(operatorContext.currentUserIdOrNull());
        statementRepository.update(statement);
        operationLogRepository.save(OperationLog.of("STATEMENT", id, "ISSUE",
                null, "签发对账单", null, operatorContext.currentUserIdOrNull(), LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(String id, String reason) {
        Statement statement = load(id);
        statement.confirm();
        statement.setUpdateBy(operatorContext.currentUserIdOrNull());
        statementRepository.update(statement);
        operationLogRepository.save(OperationLog.of("STATEMENT", id, "CONFIRM",
                null, "客户确认无误", reason, operatorContext.currentUserIdOrNull(), LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDifferent(String id, String reason) {
        Statement statement = load(id);
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("标记差异需填写差异说明");
        }
        statement.markDifferent();
        statement.setUpdateBy(operatorContext.currentUserIdOrNull());
        statementRepository.update(statement);
        operationLogRepository.save(OperationLog.of("STATEMENT", id, "DIFFERENT",
                null, "标记差异", reason, operatorContext.currentUserIdOrNull(), LocalDateTime.now()));
    }

    @Override
    public byte[] export(String id) {
        Statement statement = load(id);
        List<StatementLine> lines = statementRepository.findLines(statement.getId());
        Customer customer = customerRepository.findById(statement.getCustomerId());

        String customerName = customer == null ? "" : customer.getCustomerName();
        String period = (statement.getStartDate() == null ? "" : statement.getStartDate().toLocalDate().toString())
                + " ~ " + (statement.getEndDate() == null ? "" : statement.getEndDate().toLocalDate().toString());

        // 明细表（出货 + 退货）
        List<String> detailHeader = List.of("日期", "出货单号", "客户订单号", "物料编号", "物料名称", "规格", "单位", "数量", "单价", "金额", "备注");
        List<List<String>> detailRows = new ArrayList<>();
        // 收款记录
        List<List<String>> receiptRows = new ArrayList<>();
        for (StatementLine line : lines) {
            if (line.getLineType() == StatementLineType.RECEIVE) {
                receiptRows.add(List.of(
                        fmt(line.getBizDate()), nvl(line.getDocNo()),
                        line.getAmount() == null ? "" : line.getAmount().toPlainString()));
                continue;
            }
            detailRows.add(List.of(
                    fmt(line.getBizDate()), nvl(line.getDocNo()), nvl(line.getCustomerOrderNo()),
                    nvl(line.getMaterialNo()), nvl(line.getMaterialName()), nvl(line.getSpec()),
                    nvl(line.getUnit()),
                    line.getQty() == null ? "" : line.getQty().toPlainString(),
                    line.getUnitPrice() == null ? "" : line.getUnitPrice().toPlainString(),
                    line.getAmount() == null ? "" : line.getAmount().toPlainString(),
                    nvl(line.getRemark())));
        }

        // 汇总
        List<List<String>> summaryRows = new ArrayList<>();
        summaryRows.add(List.of("对账单号", nvl(statement.getStmtNo())));
        summaryRows.add(List.of("客户", customerName));
        summaryRows.add(List.of("对账期间", period));
        summaryRows.add(List.of("期初应收", money(statement.getOpeningBalance())));
        summaryRows.add(List.of("本期出货", money(statement.getShippedAmount())));
        summaryRows.add(List.of("本期退货/退厂", money(statement.getReturnedAmount())));
        summaryRows.add(List.of("本期收款", money(statement.getReceivedAmount())));
        summaryRows.add(List.of("期末应收", money(statement.getEndingBalance())));

        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        sheets.add(XlsxWriter.sheet("对账明细", detailHeader, detailRows));
        sheets.add(XlsxWriter.sheet("收款记录", List.of("日期", "收款单号", "金额"), receiptRows));
        sheets.add(XlsxWriter.sheet("对账单汇总", List.of("项目", "内容"), summaryRows));
        try {
            return XlsxWriter.write(sheets);
        } catch (IOException e) {
            throw new BusinessException("对账单导出失败：" + e.getMessage());
        }
    }

    /* ---------------- 私有辅助 ---------------- */

    /** 取数 → 转事实 → 调聚合引擎 */
    private StatementGenerator.StatementResult aggregate(Long customerId, LocalDateTime start, LocalDateTime end) {
        CustomerOpening opening = openingRepository.findByCustomerId(customerId);
        BigDecimal openingBalance = opening == null || opening.getOpeningBalance() == null
                ? BigDecimal.ZERO : opening.getOpeningBalance();

        List<Shipment> shipments = shipmentRepository.listByCustomerAndPeriod(customerId, start, end);
        List<StatementGenerator.ShipmentFact> shipFacts = new ArrayList<>();
        for (Shipment s : shipments) {
            ShipmentType type = s.getShipmentType() == null ? ShipmentType.SHIP : s.getShipmentType();
            BigDecimal amt = s.getAmountTaxIncluded() == null ? BigDecimal.ZERO : s.getAmountTaxIncluded();
            BigDecimal qty = s.getQty() == null ? BigDecimal.ZERO : s.getQty();
            BigDecimal unitPrice = qty.compareTo(BigDecimal.ZERO) != 0
                    ? amt.divide(qty.abs(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            shipFacts.add(new StatementGenerator.ShipmentFact(
                    s.getId(), type, s.getShipDate(), s.getProcessOrderNo(), s.getCustomerOrderNo(),
                    s.getMaterialNo(), s.getModel(), s.getSpec(), null, qty, unitPrice, amt, null));
        }

        List<Receipt> receipts = receiptRepository.listByCustomerAndPeriod(customerId, start, end);
        List<StatementGenerator.ReceiptFact> receiptFacts = new ArrayList<>();
        for (Receipt r : receipts) {
            receiptFacts.add(new StatementGenerator.ReceiptFact(
                    r.getId(), r.getReceiptDate(), r.getReceiptNo(), r.getAmount(), null));
        }

        return StatementGenerator.generate(openingBalance, shipFacts, receiptFacts);
    }

    private Statement load(String id) {
        Statement statement = statementRepository.findById(IdParser.parseId(id));
        if (statement == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "对账单不存在");
        }
        return statement;
    }

    private StatementResp toResp(Statement statement, List<StatementLine> lines) {
        StatementResp resp = new StatementResp();
        resp.setId(String.valueOf(statement.getId()));
        resp.setStmtNo(statement.getStmtNo());
        resp.setCustomerId(statement.getCustomerId() == null ? null : String.valueOf(statement.getCustomerId()));
        if (statement.getCustomerId() != null) {
            Customer customer = customerRepository.findById(statement.getCustomerId());
            resp.setCustomerName(customer == null ? null : customer.getCustomerName());
        }
        resp.setStartDate(statement.getStartDate());
        resp.setEndDate(statement.getEndDate());
        resp.setOpeningBalance(statement.getOpeningBalance());
        resp.setShippedAmount(statement.getShippedAmount());
        resp.setReturnedAmount(statement.getReturnedAmount());
        resp.setReceivedAmount(statement.getReceivedAmount());
        resp.setEndingBalance(statement.getEndingBalance());
        resp.setStatus(statement.getStatus() == null ? null : statement.getStatus().getCode());
        resp.setStatusLabel(statement.getStatus() == null ? null : statement.getStatus().getLabel());
        resp.setConfirmDate(statement.getConfirmDate());
        resp.setRemark(statement.getRemark());
        resp.setCreateTime(statement.getCreateTime());
        if (lines != null) {
            resp.setLines(toLineResp(lines));
        }
        return resp;
    }

    private List<StatementLineResp> toLineResp(List<StatementLine> lines) {
        List<StatementLineResp> result = new ArrayList<>();
        for (StatementLine line : lines) {
            result.add(toLineResp(line));
        }
        return result;
    }

    private StatementLineResp toLineResp(StatementLine line) {
        StatementLineResp r = new StatementLineResp();
        r.setId(line.getId() == null ? null : String.valueOf(line.getId()));
        r.setLineType(line.getLineType() == null ? null : line.getLineType().getCode());
        r.setLineTypeLabel(line.getLineType() == null ? null : line.getLineType().getLabel());
        r.setBizDate(line.getBizDate());
        r.setDocNo(line.getDocNo());
        r.setCustomerOrderNo(line.getCustomerOrderNo());
        r.setMaterialNo(line.getMaterialNo());
        r.setMaterialName(line.getMaterialName());
        r.setSpec(line.getSpec());
        r.setUnit(line.getUnit());
        r.setQty(line.getQty());
        r.setUnitPrice(line.getUnitPrice());
        r.setAmount(line.getAmount());
        r.setRemark(line.getRemark());
        r.setSourceId(line.getSourceId() == null ? null : String.valueOf(line.getSourceId()));
        r.setSortNo(line.getSortNo());
        return r;
    }

    /** 预览行来自聚合引擎（StatementGenerator.StatementLine 记录），独立映射避免类型强转 */
    private List<StatementLineResp> toLineRespFromGen(List<StatementGenerator.StatementLine> lines) {
        List<StatementLineResp> result = new ArrayList<>();
        for (StatementGenerator.StatementLine line : lines) {
            StatementLineResp r = new StatementLineResp();
            r.setLineType(line.lineType() == null ? null : line.lineType().getCode());
            r.setLineTypeLabel(line.lineType() == null ? null : line.lineType().getLabel());
            r.setBizDate(line.bizDate());
            r.setDocNo(line.docNo());
            r.setCustomerOrderNo(line.customerOrderNo());
            r.setMaterialNo(line.materialNo());
            r.setMaterialName(line.materialName());
            r.setSpec(line.spec());
            r.setUnit(line.unit());
            r.setQty(line.qty());
            r.setUnitPrice(line.unitPrice());
            r.setAmount(line.amount());
            r.setRemark(line.remark());
            r.setSourceId(line.sourceId() == null ? null : String.valueOf(line.sourceId()));
            r.setSortNo(line.sortNo());
            result.add(r);
        }
        return result;
    }

    private String nextStmtNo() {
        String prefix = "ST" + LocalDate.now().format(DATE_FMT);
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + String.format("%04d", (int) (Math.random() * 9000) + 1000);
            // 对账单号唯一由 biz_statement.uk_stmt_no 兜底；此处仅降低碰撞概率
            return candidate;
        }
        return prefix + System.currentTimeMillis();
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.toLocalDate().toString();
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    private String money(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }
}
