package com.example.admin.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.service.ReceiptAppService;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.receipt.Receipt;
import com.example.admin.domain.model.receipt.ReceiptQuery;
import com.example.admin.domain.model.receipt.ReceiptStatus;
import com.example.admin.domain.model.reconciliation.CustomerOpening;
import com.example.admin.domain.repository.CustomerOpeningRepository;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.OperationLogRepository;
import com.example.admin.domain.repository.ReceiptRepository;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.interfaces.dto.req.CustomerOpeningSaveReq;
import com.example.admin.interfaces.dto.req.ReceiptPageQuery;
import com.example.admin.interfaces.dto.req.ReceiptSaveReq;
import com.example.admin.interfaces.dto.resp.CustomerOpeningResp;
import com.example.admin.interfaces.dto.resp.ReceiptResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 收款登记应用服务实现。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReceiptAppServiceImpl implements ReceiptAppService {

    private final ReceiptRepository receiptRepository;
    private final CustomerOpeningRepository openingRepository;
    private final CustomerRepository customerRepository;
    private final OperationLogRepository operationLogRepository;
    private final OperatorContext operatorContext;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public PageResult<ReceiptResp> page(ReceiptPageQuery query) {
        ReceiptQuery dq = new ReceiptQuery();
        dq.setKeyword(query.getKeyword());
        dq.setCustomerId(query.getCustomerId());
        dq.setBeginDate(query.getBeginDate());
        dq.setEndDate(query.getEndDate());
        dq.setStatus(query.getStatus());
        dq.setPageNum(query.getPageNum());
        dq.setPageSize(query.getPageSize());
        dq.setSortBy(query.getSortBy());
        dq.setSortOrder(query.getSortOrder());
        PageResult<Receipt> page = receiptRepository.page(dq);
        List<ReceiptResp> records = new ArrayList<>();
        for (Receipt receipt : page.getRecords()) {
            records.add(toResp(receipt));
        }
        PageResult<ReceiptResp> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        return result;
    }

    @Override
    public ReceiptResp detail(String id) {
        Receipt receipt = receiptRepository.findById(IdParser.parseId(id));
        if (receipt == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "收款记录不存在");
        }
        return toResp(receipt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(ReceiptSaveReq req) {
        Long customerId = IdParser.parseId(req.getCustomerId());
        Customer customer = customerRepository.findById(customerId);
        if (customer == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在：" + req.getCustomerId());
        }
        Long operatorId = operatorContext.currentUserIdOrNull();
        if (StrUtil.isNotBlank(req.getId())) {
            // 更新：仅允许未作废的收款调整金额/备注
            Receipt exist = receiptRepository.findById(IdParser.parseId(req.getId()));
            if (exist == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "收款记录不存在");
            }
            if (exist.isVoid()) {
                throw new BusinessException("已作废的收款不可修改");
            }
            exist.setReceiptDate(req.getReceiptDate());
            exist.setAmount(req.getAmount());
            exist.setCurrency(req.getCurrency());
            exist.setSettleMethod(req.getSettleMethod());
            exist.setInvoiceNo(req.getInvoiceNo());
            exist.setRemark(req.getRemark());
            exist.setUpdateBy(operatorId);
            receiptRepository.update(exist);
            operationLogRepository.save(OperationLog.of("RECEIPT", String.valueOf(exist.getId()), "UPDATE",
                    null, describe(exist), null, operatorId, LocalDateTime.now()));
            return String.valueOf(exist.getId());
        }
        Receipt receipt = new Receipt();
        receipt.setReceiptNo(nextReceiptNo());
        receipt.setCustomerId(customerId);
        receipt.setReceiptDate(req.getReceiptDate() == null
                ? LocalDateTime.of(LocalDate.now(), LocalDateTime.MIN.toLocalTime()) : req.getReceiptDate());
        receipt.setAmount(req.getAmount());
        receipt.setCurrency(StrUtil.isBlank(req.getCurrency()) ? "CNY" : req.getCurrency());
        receipt.setSettleMethod(req.getSettleMethod());
        receipt.setInvoiceNo(req.getInvoiceNo());
        receipt.setAllocatedAmount(BigDecimal.ZERO);
        receipt.setStatus(ReceiptStatus.NORMAL);
        receipt.setRemark(req.getRemark());
        receipt.setCreateBy(operatorId);
        receipt.setUpdateBy(operatorId);
        Long id = receiptRepository.save(receipt);
        operationLogRepository.save(OperationLog.of("RECEIPT", String.valueOf(id), "CREATE",
                null, describe(receipt), null, operatorId, LocalDateTime.now()));
        return String.valueOf(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidReceipt(String id, String reason) {
        Long receiptId = IdParser.parseId(id);
        Receipt receipt = receiptRepository.findById(receiptId);
        if (receipt == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "收款记录不存在");
        }
        if (receipt.isVoid()) {
            throw new BusinessException("该收款已作废，无需重复操作");
        }
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("作废收款属于敏感操作，请填写原因");
        }
        String before = describe(receipt);
        receipt.void_(reason);
        receipt.setUpdateBy(operatorContext.currentUserIdOrNull());
        receiptRepository.update(receipt);
        operationLogRepository.save(OperationLog.of("RECEIPT", String.valueOf(receiptId), "VOID",
                before, describe(receipt), reason, operatorContext.currentUserIdOrNull(), LocalDateTime.now()));
    }

    @Override
    public CustomerOpeningResp getOpening(String customerId) {
        CustomerOpening opening = openingRepository.findByCustomerId(IdParser.parseId(customerId));
        if (opening == null) {
            return null;
        }
        return toOpeningResp(opening);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOpening(CustomerOpeningSaveReq req) {
        Long customerId = IdParser.parseId(req.getCustomerId());
        if (customerRepository.findById(customerId) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在：" + req.getCustomerId());
        }
        Long operatorId = operatorContext.currentUserIdOrNull();
        CustomerOpening exist = openingRepository.findByCustomerId(customerId);
        if (exist != null) {
            exist.setAsOfDate(req.getAsOfDate());
            exist.setOpeningBalance(req.getOpeningBalance());
            exist.setCurrency(StrUtil.isBlank(req.getCurrency()) ? "CNY" : req.getCurrency());
            exist.setRemark(req.getRemark());
            exist.setUpdateBy(operatorId);
            openingRepository.update(exist);
            return;
        }
        CustomerOpening opening = new CustomerOpening();
        opening.setCustomerId(customerId);
        opening.setAsOfDate(req.getAsOfDate());
        opening.setOpeningBalance(req.getOpeningBalance());
        opening.setCurrency(StrUtil.isBlank(req.getCurrency()) ? "CNY" : req.getCurrency());
        opening.setRemark(req.getRemark());
        opening.setCreateBy(operatorId);
        opening.setUpdateBy(operatorId);
        openingRepository.save(opening);
    }

    /* ---------------- 私有辅助 ---------------- */

    private ReceiptResp toResp(Receipt receipt) {
        ReceiptResp resp = new ReceiptResp();
        resp.setId(String.valueOf(receipt.getId()));
        resp.setReceiptNo(receipt.getReceiptNo());
        resp.setCustomerId(receipt.getCustomerId() == null ? null : String.valueOf(receipt.getCustomerId()));
        if (receipt.getCustomerId() != null) {
            Customer customer = customerRepository.findById(receipt.getCustomerId());
            resp.setCustomerName(customer == null ? null : customer.getCustomerName());
        }
        resp.setReceiptDate(receipt.getReceiptDate());
        resp.setAmount(receipt.getAmount());
        resp.setCurrency(receipt.getCurrency());
        resp.setSettleMethod(receipt.getSettleMethod());
        resp.setInvoiceNo(receipt.getInvoiceNo());
        resp.setAllocatedAmount(receipt.getAllocatedAmount());
        resp.setStatus(receipt.getStatus() == null ? null : receipt.getStatus().getCode());
        resp.setStatusLabel(receipt.getStatus() == null ? null : receipt.getStatus().getLabel());
        resp.setRemark(receipt.getRemark());
        resp.setCreateTime(receipt.getCreateTime());
        return resp;
    }

    private CustomerOpeningResp toOpeningResp(CustomerOpening opening) {
        CustomerOpeningResp resp = new CustomerOpeningResp();
        resp.setId(String.valueOf(opening.getId()));
        resp.setCustomerId(String.valueOf(opening.getCustomerId()));
        Customer customer = customerRepository.findById(opening.getCustomerId());
        resp.setCustomerName(customer == null ? null : customer.getCustomerName());
        resp.setAsOfDate(opening.getAsOfDate());
        resp.setOpeningBalance(opening.getOpeningBalance());
        resp.setCurrency(opening.getCurrency());
        resp.setRemark(opening.getRemark());
        return resp;
    }

    private String nextReceiptNo() {
        String prefix = "RK" + LocalDate.now().format(DATE_FMT);
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + String.format("%04d", (int) (Math.random() * 9000) + 1000);
            if (receiptRepository.findByReceiptNo(candidate) == null) {
                return candidate;
            }
        }
        return prefix + System.currentTimeMillis();
    }

    private String describe(Receipt receipt) {
        return "收款[" + receipt.getReceiptNo() + " " + (receipt.getAmount() == null ? "" : receipt.getAmount()) + " "
                + (receipt.getStatus() == null ? "" : receipt.getStatus().getLabel()) + "]";
    }
}
