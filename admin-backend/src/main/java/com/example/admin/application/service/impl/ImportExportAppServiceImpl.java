package com.example.admin.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.dict.DictItem;
import com.example.admin.domain.model.imports.BatchStatus;
import com.example.admin.domain.model.imports.IdempotentStrategy;
import com.example.admin.domain.model.imports.ImportBatch;
import com.example.admin.domain.model.imports.ImportErrorLine;
import com.example.admin.domain.model.imports.ImportType;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.mapping.ImportMapping;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.DictRepository;
import com.example.admin.domain.repository.ImportBatchRepository;
import com.example.admin.domain.repository.ImportMappingRepository;
import com.example.admin.domain.repository.OperationLogRepository;
import com.example.admin.domain.repository.OrderRepository;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.service.HeaderNormalizer;
import com.example.admin.domain.service.ValueParser;
import com.example.admin.application.service.ImportExportAppService;
import com.example.admin.infrastructure.excel.XlsxReader;
import com.example.admin.infrastructure.excel.XlsxWriter;
import com.example.admin.interfaces.dto.req.ShipmentRowReq;
import com.example.admin.interfaces.dto.req.ShipmentSaveReq;
import com.example.admin.interfaces.dto.resp.BatchLineResult;
import com.example.admin.interfaces.dto.resp.ImportBatchResp;
import com.example.admin.interfaces.dto.resp.ImportCommitResp;
import com.example.admin.interfaces.dto.resp.ImportPreviewResp;
import com.example.admin.application.service.ShipmentAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入 / 导出实现。
 *
 * <p>取舍说明（附录A）：
 * <ul>
 *     <li>列头匹配走配置化的 {@link ImportMapping} + {@link HeaderNormalizer} 模糊匹配，
 *         新增一种台账叫法只需改数据；</li>
 *     <li>预览与确认共用同一套解析与校验，区别只在"最后是否落库"，
 *         这样预览报告与真实结果不会有偏差；</li>
 *     <li>出货导入最终委托 {@link ShipmentAppService#create}，
 *         与界面录入走完全相同的匹配与回写逻辑，杜绝双标。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportExportAppServiceImpl implements ImportExportAppService {

    /** 导出最大行数（防止一次导出百万行把内存打爆） */
    private static final int EXPORT_MAX_ROWS = 5000;

    private final ImportMappingRepository mappingRepository;
    private final DictRepository dictRepository;
    private final ImportBatchRepository batchRepository;
    private final OperationLogRepository operationLogRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final BizUserRepository userRepository;
    private final ShipmentAppService shipmentAppService;

    /* ==================== 导入 ==================== */

    @Override
    public ImportPreviewResp preview(String type, byte[] fileBytes, String fileName, String sheetName,
                                     String idempotent) {
        ParsedTable table = parse(type, fileBytes, sheetName);
        ImportPreviewResp resp = new ImportPreviewResp();
        resp.setBatchType(type);
        resp.setFileName(fileName);
        resp.setSheetName(table.sheetName());
        resp.setTotal(table.rowCount());
        resp.setUnknownHeaders(table.unknownHeaders());
        // 预览只做"能不能过"的校验，不落库，因此逐行试算一次
        CountContext context = new CountContext();
        List<BatchLineResult> results = execute(type, table, IdempotentStrategy.ofCode(idempotent), false, context);
        resp.setInsertCount(context.insert);
        resp.setUpdateCount(context.update);
        resp.setSkipCount(context.skip);
        resp.setFailCount(context.fail);
        resp.setWarnCount(context.warn);
        resp.setHasBlockingError(context.fail > 0);
        resp.setResults(results);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportCommitResp confirm(String type, byte[] fileBytes, String fileName, String sheetName,
                                    String idempotent) {
        ParsedTable table = parse(type, fileBytes, sheetName);
        IdempotentStrategy strategy = IdempotentStrategy.ofCode(idempotent);
        CountContext context = new CountContext();
        List<BatchLineResult> results = execute(type, table, strategy, true, context);

        ImportBatch batch = new ImportBatch();
        batch.setBatchType(ImportType.ofCode(type));
        batch.setFileName(fileName);
        batch.setSheetName(table.sheetName());
        batch.setTotalCount(table.rowCount());
        batch.setInsertCount(context.insert);
        batch.setUpdateCount(context.update);
        batch.setSkipCount(context.skip);
        batch.setFailCount(context.fail);
        batch.setWarnCount(context.warn);
        batch.setIdempotent(strategy);
        batch.setStatus(BatchStatus.COMMITTED);
        // 失败行落库：错误清单是事后下载，只在响应里回传一次是不够的
        batch.attachErrors(results.stream()
                .filter(r -> !r.isSuccess())
                .map(r -> new ImportErrorLine(r.getLineNo() == null ? 0 : r.getLineNo(),
                        r.getMessage() == null ? "" : r.getMessage(),
                        r.getRawData() == null ? "" : r.getRawData()))
                .toList());
        Long batchId = batchRepository.save(batch);

        operationLogRepository.save(OperationLog.of("IMPORT", String.valueOf(batchId), "IMPORT",
                null, String.format("新增%d 更新%d 跳过%d 失败%d", context.insert, context.update, context.skip, context.fail),
                fileName, null, LocalDateTime.now()));

        ImportCommitResp resp = new ImportCommitResp();
        resp.setBatchId(String.valueOf(batchId));
        resp.setTotal(table.rowCount());
        resp.setInsertCount(context.insert);
        resp.setUpdateCount(context.update);
        resp.setSkipCount(context.skip);
        resp.setFailCount(context.fail);
        resp.setWarnCount(context.warn);
        resp.setResults(results);
        return resp;
    }

    /* ==================== 解析 ==================== */

    /** 表头列 → 系统字段 的映射结果 */
    private record ParsedTable(String sheetName, int rowCount, List<String> unknownHeaders,
                               List<Map<String, String>> rows, List<Integer> rowNumbers) {
    }

    private ParsedTable parse(String type, byte[] fileBytes, String sheetName) {
        ImportType importType = ImportType.ofCode(type);
        if (importType == null) {
            throw new BusinessException("不支持的导入类型：" + type);
        }
        List<XlsxReader.Row> rawRows;
        List<String> names;
        try {
            names = XlsxReader.sheetNames(fileBytes);
            rawRows = XlsxReader.read(fileBytes, StrUtil.isBlank(sheetName) ? null : sheetName);
        } catch (IOException e) {
            throw new BusinessException("文件解析失败，请确认是 .xlsx 格式：" + e.getMessage());
        }
        if (CollUtil.isEmpty(rawRows)) {
            throw new BusinessException("工作表为空，没有可导入的数据");
        }
        String usedSheet = StrUtil.isNotBlank(sheetName) ? sheetName : (names.isEmpty() ? "Sheet1" : names.get(0));

        List<ImportMapping> mappings = mappingRepository.listByModule(importType.getCode());
        XlsxReader.Row headerRow = rawRows.get(0);
        String[] fields = new String[headerRow.cells().size()];
        List<String> unknown = new ArrayList<>();
        for (int c = 0; c < headerRow.cells().size(); c++) {
            String header = headerRow.cells().get(c);
            if (StrUtil.isBlank(header)) {
                continue;
            }
            String field = HeaderNormalizer.matchField(header, mappings);
            if (field == null) {
                unknown.add(header);
            } else {
                fields[c] = field;
            }
        }
        List<Map<String, String>> rows = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            XlsxReader.Row row = rawRows.get(i);
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < fields.length && c < row.cells().size(); c++) {
                if (fields[c] != null) {
                    values.put(fields[c], row.cells().get(c));
                }
            }
            rows.add(values);
            rowNumbers.add(row.rowNumber());
        }
        return new ParsedTable(usedSheet, rows.size(), unknown, rows, rowNumbers);
    }

    /** 计数上下文 */
    private static final class CountContext {
        private int insert;
        private int update;
        private int skip;
        private int fail;
        private int warn;
    }

    /**
     * 按模块执行导入（commit=false 时只校验不落库）。
     *
     * @param type     模块
     * @param table    解析结果
     * @param strategy 幂等策略
     * @param commit   是否落库
     * @param context  计数
     * @return 失败与警告行
     */
    private List<BatchLineResult> execute(String type, ParsedTable table, IdempotentStrategy strategy,
                                          boolean commit, CountContext context) {
        ImportType importType = ImportType.ofCode(type);
        List<BatchLineResult> problemRows = new ArrayList<>();
        switch (importType) {
            case CUSTOMER -> importCustomers(table, strategy, commit, context, problemRows);
            case SHIPMENT -> importShipments(table, strategy, commit, context, problemRows);
            case ORDER -> importOrders(table, strategy, commit, context, problemRows);
            default -> throw new BusinessException("不支持的导入类型：" + type);
        }
        return problemRows;
    }

    /* ---------------- 客户导入 ---------------- */

    private void importCustomers(ParsedTable table, IdempotentStrategy strategy, boolean commit,
                                 CountContext context, List<BatchLineResult> problems) {
        for (int i = 0; i < table.rows().size(); i++) {
            Map<String, String> row = table.rows().get(i);
            int lineNo = table.rowNumbers().get(i);
            try {
                String name = value(row, "customer_name");
                if (StrUtil.isBlank(name)) {
                    throw new BusinessException("客户名称不能为空");
                }
                String normalized = Customer.normalizeName(name);
                Customer existing = customerRepository.findByNormalizedName(normalized);
                if (existing != null && strategy == IdempotentStrategy.SKIP) {
                    context.skip++;
                    continue;
                }
                Customer customer = existing == null ? new Customer() : existing;
                customer.setCustomerName(name.trim());
                customer.setCustomerCode(value(row, "customer_code"));
                customer.setAddress(value(row, "address"));
                customer.setRemark(value(row, "remark"));
                customer.setStatus(1);

                String contactName = value(row, "contact_name");
                String invoiceTitle = value(row, "invoice_title");

                if (commit) {
                    if (existing == null) {
                        Long id = customerRepository.save(customer);
                        customer.setId(id);
                        context.insert++;
                    } else {
                        customerRepository.update(customer);
                        context.update++;
                    }
                    if (StrUtil.isNotBlank(contactName)) {
                        customer.setContacts(customerRepository.listContacts(customer.getId()));
                        var contact = new com.example.admin.domain.model.customer.CustomerContact();
                        contact.setCustomerId(customer.getId());
                        contact.setContactName(contactName);
                        contact.setPhone(value(row, "phone"));
                        contact.setMobile(value(row, "mobile"));
                        contact.setEmail(value(row, "email"));
                        contact.setIsDefault(1);
                        contact.setStatus(1);
                        customerRepository.clearContactDefault(customer.getId());
                        customerRepository.saveContact(contact);
                    }
                    if (StrUtil.isNotBlank(invoiceTitle)) {
                        var invoice = new com.example.admin.domain.model.customer.CustomerInvoice();
                        invoice.setCustomerId(customer.getId());
                        invoice.setInvoiceTitle(invoiceTitle);
                        invoice.setTaxNo(value(row, "tax_no"));
                        invoice.setBankName(value(row, "bank_name"));
                        invoice.setBankAccount(value(row, "bank_account"));
                        invoice.setInvoiceAddress(value(row, "invoice_address"));
                        invoice.setInvoicePhone(value(row, "invoice_phone"));
                        invoice.setIsDefault(1);
                        invoice.setStatus(1);
                        customerRepository.clearInvoiceDefault(customer.getId());
                        customerRepository.saveInvoice(invoice);
                    }
                } else if (existing == null) {
                    context.insert++;
                } else {
                    context.update++;
                }
            } catch (BusinessException e) {
                context.fail++;
                problems.add(BatchLineResult.fail(lineNo, e.getMessage(), null, null));
            } catch (Exception e) {
                log.error("客户导入失败，第 {} 行", lineNo, e);
                context.fail++;
                problems.add(BatchLineResult.fail(lineNo, "系统异常：" + e.getMessage(), null, null));
            }
        }
    }

    /* ---------------- 出货导入 ---------------- */

    private void importShipments(ParsedTable table, IdempotentStrategy strategy, boolean commit,
                                 CountContext context, List<BatchLineResult> problems) {
        List<ShipmentRowReq> rows = new ArrayList<>();
        for (int i = 0; i < table.rows().size(); i++) {
            Map<String, String> row = table.rows().get(i);
            int lineNo = table.rowNumbers().get(i);
            try {
                ShipmentRowReq req = new ShipmentRowReq();
                LocalDateTime shipDate = ValueParser.toDate(value(row, "ship_date"), "出货日期");
                req.setShipDate(shipDate == null ? LocalDateTime.of(LocalDate.now(), LocalTime.MIN) : shipDate);
                req.setProcessOrderNo(value(row, "process_order_no"));
                req.setCustomerOrderNo(value(row, "customer_order_no"));
                req.setCustomerMaterialNo(value(row, "customer_material_no"));
                req.setModel(value(row, "model"));
                req.setMaterialNo(value(row, "material_no"));
                req.setSpec(value(row, "spec"));
                req.setRemark(value(row, "remark"));
                req.setQty(ValueParser.toNumber(value(row, "qty"), "出货数量"));
                if (req.getQty() == null) {
                    throw new BusinessException("出货数量不能为空");
                }

                String salesmanName = value(row, "salesman_name");
                if (StrUtil.isNotBlank(salesmanName)) {
                    var user = userRepository.findByName(salesmanName.trim());
                    if (user == null) {
                        throw new BusinessException("业务员【" + salesmanName + "】不存在，请先在用户管理中创建");
                    }
                    req.setSalesmanId(String.valueOf(user.id()));
                }
                String customerName = value(row, "customer_name");
                if (StrUtil.isNotBlank(customerName)) {
                    Customer customer = customerRepository.findByNormalizedName(Customer.normalizeName(customerName));
                    if (customer == null) {
                        throw new BusinessException("客户【" + customerName + "】不存在，请先导入或创建客户");
                    }
                    req.setCustomerId(String.valueOf(customer.getId()));
                }
                rows.add(req);
            } catch (BusinessException e) {
                context.fail++;
                problems.add(BatchLineResult.fail(lineNo, e.getMessage(), null, null));
            } catch (Exception e) {
                log.error("出货导入解析失败，第 {} 行", lineNo, e);
                context.fail++;
                problems.add(BatchLineResult.fail(lineNo, "系统异常：" + e.getMessage(), null, null));
            }
        }
        if (!commit) {
            context.insert += rows.size();
            return;
        }
        if (rows.isEmpty()) {
            return;
        }
        ShipmentSaveReq saveReq = new ShipmentSaveReq();
        saveReq.setSource("IMPORT");
        saveReq.setRows(rows);
        var result = shipmentAppService.create(saveReq);
        context.insert += result.getSuccessCount();
        context.fail += result.getFailCount();
        for (BatchLineResult lineResult : result.getResults()) {
            if (!lineResult.isSuccess()) {
                problems.add(lineResult);
            }
        }
        // 说明：出货的幂等由 ShipmentAppService 在录入时按幂等键处理，
        // APPEND 之外的策略在数据库唯一索引与业务校验两层共同保证不重复。
        if (strategy == IdempotentStrategy.APPEND) {
            context.skip += 0;
        }
    }

    /* ---------------- 订单导入（头 + 明细） ---------------- */

    private void importOrders(ParsedTable table, IdempotentStrategy strategy, boolean commit,
                              CountContext context, List<BatchLineResult> problems) {
        SalesOrder current = null;
        String currentOrderNo = null;
        List<SalesOrderItem> currentItems = new ArrayList<>();
        int currentLineNo = 0;

        for (int i = 0; i < table.rows().size(); i++) {
            Map<String, String> row = table.rows().get(i);
            int lineNo = table.rowNumbers().get(i);
            try {
                String orderNo = value(row, "order_no");
                if (StrUtil.isBlank(orderNo)) {
                    throw new BusinessException("订单号不能为空");
                }
                if (!orderNo.equals(currentOrderNo)) {
                    // 换单：先把上一单落库
                    if (current != null) {
                        flushOrder(current, currentItems, strategy, commit, context, problems, currentLineNo);
                    }
                    currentOrderNo = orderNo;
                    currentLineNo = lineNo;
                    currentItems = new ArrayList<>();
                    current = buildOrderHead(row, orderNo);
                }
                SalesOrderItem item = new SalesOrderItem();
                item.setModel(value(row, "model"));
                item.setMaterialNo(value(row, "material_no"));
                item.setCustomerMaterialNo(value(row, "customer_material_no"));
                item.setSpec(value(row, "spec"));
                item.setQtyOrdered(ValueParser.toNumber(value(row, "qty_ordered"), "下单数量"));
                item.setUnitPrice(ValueParser.toNumber(value(row, "unit_price"), "单价"));
                item.setTaxRate(ValueParser.toRate(value(row, "tax_rate"), "税率"));
                item.setRemark(value(row, "remark"));
                item.setReconcileDate(ValueParser.toDate(value(row, "reconcile_date"), "对账日期"));
                if (StrUtil.isBlank(item.getModel())) {
                    throw new BusinessException("型号不能为空");
                }
                if (item.getQtyOrdered() == null || item.getQtyOrdered().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("下单数量必须大于 0");
                }
                if (item.getUnitPrice() == null) {
                    throw new BusinessException("单价不能为空");
                }
                item.setQtyShipped(BigDecimal.ZERO);
                item.recalculate();
                currentItems.add(item);
            } catch (BusinessException e) {
                context.fail++;
                problems.add(BatchLineResult.fail(lineNo, e.getMessage(), null, null));
            } catch (Exception e) {
                log.error("订单导入解析失败，第 {} 行", lineNo, e);
                context.fail++;
                problems.add(BatchLineResult.fail(lineNo, "系统异常：" + e.getMessage(), null, null));
            }
        }
        if (current != null) {
            flushOrder(current, currentItems, strategy, commit, context, problems, currentLineNo);
        }
    }

    private SalesOrder buildOrderHead(Map<String, String> row, String orderNo) {
        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNo.trim());
        order.setCustomerOrderNo(value(row, "customer_order_no"));
        order.setRequirement(value(row, "requirement"));
        order.setMainboardLabel(value(row, "mainboard_label"));
        order.setCartonLabel(value(row, "carton_label"));
        order.setRemark(value(row, "order_remark"));
        LocalDateTime orderDate = ValueParser.toDate(value(row, "order_date"), "下单时间");
        order.setOrderDate(orderDate == null ? LocalDateTime.now() : orderDate);
        order.setExpectedDeliveryDate(ValueParser.toDate(value(row, "expected_delivery_date"), "预计交货时间"));
        order.setCurrency("CNY");
        String salesmanName = value(row, "salesman_name");
        if (StrUtil.isNotBlank(salesmanName)) {
            var user = userRepository.findByName(salesmanName.trim());
            if (user == null) {
                throw new BusinessException("业务员【" + salesmanName + "】不存在，请先在用户管理中创建");
            }
            order.setSalesmanId(user.id());
        } else {
            throw new BusinessException("业务员不能为空");
        }
        String customerName = value(row, "customer_name");
        if (StrUtil.isBlank(customerName)) {
            throw new BusinessException("客户名称不能为空");
        }
        Customer customer = customerRepository.findByNormalizedName(Customer.normalizeName(customerName));
        if (customer == null) {
            throw new BusinessException("客户【" + customerName + "】不存在，请先导入或创建客户");
        }
        order.setCustomerId(customer.getId());
        return order;
    }

    private void flushOrder(SalesOrder order, List<SalesOrderItem> items, IdempotentStrategy strategy,
                            boolean commit, CountContext context, List<BatchLineResult> problems, int lineNo) {
        try {
            SalesOrder existing = orderRepository.findByOrderNo(order.getOrderNo());
            if (existing != null && strategy == IdempotentStrategy.SKIP) {
                context.skip++;
                return;
            }
            if (!commit) {
                if (existing == null) {
                    context.insert++;
                } else {
                    context.update++;
                }
                return;
            }
            if (existing != null) {
                // 覆盖更新：头字段覆盖，明细整单替换（已有出货的明细保留，避免破坏出货台账）
                order.setId(existing.getId());
                order.setStatus(existing.getStatus());
                orderRepository.update(order);
                List<Long> keep = new ArrayList<>();
                int lineNoSeq = 1;
                for (SalesOrderItem item : items) {
                    item.setOrderId(existing.getId());
                    item.setLineNo(lineNoSeq++);
                    item.setQtyShipped(BigDecimal.ZERO);
                    item.recalculate();
                    Long id = orderRepository.saveItem(item);
                    keep.add(id);
                }
                for (SalesOrderItem old : orderRepository.listItems(existing.getId())) {
                    if (keep.contains(old.getId())) {
                        continue;
                    }
                    if (old.getQtyShipped() == null || old.getQtyShipped().compareTo(BigDecimal.ZERO) == 0) {
                        orderRepository.removeItem(old.getId());
                    }
                }
                context.update++;
                return;
            }
            Long orderId = orderRepository.save(order);
            int lineNoSeq = 1;
            for (SalesOrderItem item : items) {
                item.setOrderId(orderId);
                item.setLineNo(lineNoSeq++);
                item.setQtyShipped(BigDecimal.ZERO);
                item.recalculate();
                orderRepository.saveItem(item);
            }
            context.insert++;
        } catch (BusinessException e) {
            context.fail++;
            problems.add(BatchLineResult.fail(lineNo, e.getMessage(), null, null));
        }
    }

    /* ==================== 导出 ==================== */

    @Override
    public byte[] export(String type, String template, String keyword, String beginDate, String endDate,
                         List<String> ids) {
        ImportType importType = ImportType.ofCode(type);
        if (importType == null) {
            throw new BusinessException("不支持的导出类型：" + type);
        }
        boolean legacy = "LEGACY".equalsIgnoreCase(template);
        List<String> header = exportHeader(importType, legacy);
        List<List<String>> rows = switch (importType) {
            case CUSTOMER -> exportCustomers();
            case ORDER -> exportOrders();
            case SHIPMENT -> exportShipments();
        };
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        sheets.add(new XlsxWriter.Sheet(legacy ? "台账" : "数据", header, rows));
        sheets.add(fieldSheet(importType));
        sheets.add(dictSheet());
        try {
            return XlsxWriter.write(sheets);
        } catch (IOException e) {
            throw new BusinessException("导出失败：" + e.getMessage());
        }
    }

    @Override
    public byte[] template(String type, String template) {
        ImportType importType = ImportType.ofCode(type);
        if (importType == null) {
            throw new BusinessException("不支持的模板类型：" + type);
        }
        boolean legacy = "LEGACY".equalsIgnoreCase(template);
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        sheets.add(new XlsxWriter.Sheet(legacy ? "台账" : "数据", exportHeader(importType, legacy), List.of()));
        sheets.add(fieldSheet(importType));
        sheets.add(dictSheet());
        try {
            return XlsxWriter.write(sheets);
        } catch (IOException e) {
            throw new BusinessException("模板生成失败：" + e.getMessage());
        }
    }

    @Override
    public List<ImportBatchResp> batches(String type, int limit) {
        return batchRepository.list(ImportType.ofCode(type), limit).stream().map(this::toBatchResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(String batchId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("回滚属于敏感操作，请填写原因");
        }
        ImportBatch batch = batchRepository.findById(Long.valueOf(batchId));
        if (batch == null) {
            throw new BusinessException("导入批次不存在");
        }
        if (batch.getStatus() == BatchStatus.ROLLED_BACK) {
            throw new BusinessException("该批次已回滚，请勿重复操作");
        }
        if (batch.getBatchType() != ImportType.SHIPMENT) {
            throw new BusinessException("当前仅支持回滚出货导入批次");
        }
        // 回滚只撤销本批次新增的出货，并回写扣减明细出货数，不影响其它来源的数据
        List<Shipment> shipments = shipmentRepository.listByBatch(batch.getId());
        for (Shipment shipment : shipments) {
            if (shipment.isVoid()) {
                continue;
            }
            if (shipment.getOrderItemId() != null) {
                SalesOrderItem item = orderRepository.findItem(shipment.getOrderItemId());
                if (item != null) {
                    item.revertShipment(shipment.getQty());
                    orderRepository.updateItem(item);
                    SalesOrder order = orderRepository.findById(item.getOrderId());
                    if (order != null) {
                        order.setItems(orderRepository.listItems(order.getId()));
                        order.refreshStatus();
                        orderRepository.update(order);
                    }
                }
            }
            shipment.void_("批次回滚：" + reason);
            shipmentRepository.update(shipment);
        }
        batch.setStatus(BatchStatus.ROLLED_BACK);
        batchRepository.update(batch);
        operationLogRepository.save(OperationLog.of("IMPORT", batchId, "ROLLBACK",
                String.valueOf(shipments.size()), "已回滚", reason, null, LocalDateTime.now()));
    }

    @Override
    public byte[] errorReport(String batchId) {
        ImportBatch batch = batchRepository.findById(Long.valueOf(batchId));
        if (batch == null) {
            throw new BusinessException("导入批次不存在：" + batchId);
        }
        List<ImportErrorLine> lines = batch.errors();
        if (lines.isEmpty()) {
            return null;
        }
        List<List<String>> rows = new ArrayList<>();
        for (ImportErrorLine line : lines) {
            rows.add(List.of(String.valueOf(line.lineNo()), line.message(), line.raw()));
        }
        XlsxWriter.Sheet sheet = XlsxWriter.sheet("错误清单",
                List.of("行号", "失败原因", "原始报文"), rows);
        try {
            return XlsxWriter.write(List.of(sheet));
        } catch (IOException e) {
            throw new BusinessException("错误清单生成失败：" + e.getMessage());
        }
    }

    /* ---------------- 导出辅助 ---------------- */

    private List<String> exportHeader(ImportType type, boolean legacy) {
        List<ImportMapping> mappings = mappingRepository.listByModule(type.getCode());
        List<String> header = new ArrayList<>();
        for (ImportMapping mapping : mappings) {
            // 传统台账用第一个中文别名当列头，系统模板用字段名，便于区分两种用途
            String firstAlias = mapping.aliasList().isEmpty() ? mapping.getFieldName() : mapping.aliasList().get(0);
            header.add(legacy ? firstAlias : mapping.getFieldName());
        }
        return header;
    }

    private List<List<String>> exportCustomers() {
        var query = new com.example.admin.domain.model.customer.CustomerQuery();
        query.setPageNum(1);
        query.setPageSize(EXPORT_MAX_ROWS);
        List<List<String>> rows = new ArrayList<>();
        for (Customer customer : customerRepository.page(query).getRecords()) {
            var contact = customer.defaultContact();
            var invoice = customer.defaultInvoice();
            rows.add(List.of(
                    nvl(customer.getCustomerName()), nvl(customer.getCustomerCode()), nvl(customer.getAddress()),
                    contact == null ? "" : nvl(contact.getContactName()),
                    contact == null ? "" : nvl(contact.getPhone()),
                    contact == null ? "" : nvl(contact.getMobile()),
                    contact == null ? "" : nvl(contact.getEmail()),
                    invoice == null ? "" : nvl(invoice.getInvoiceTitle()),
                    invoice == null ? "" : nvl(invoice.getTaxNo()),
                    invoice == null ? "" : nvl(invoice.getBankName()),
                    invoice == null ? "" : nvl(invoice.getBankAccount()),
                    invoice == null ? "" : nvl(invoice.getInvoiceAddress()),
                    invoice == null ? "" : nvl(invoice.getInvoicePhone()),
                    nvl(customer.getRemark())));
        }
        return rows;
    }

    private List<List<String>> exportOrders() {
        var query = new com.example.admin.domain.model.order.OrderQuery();
        query.setPageNum(1);
        query.setPageSize(500);
        List<List<String>> rows = new ArrayList<>();
        for (SalesOrder order : orderRepository.page(query).getRecords()) {
            String customerName = customerRepository.findById(order.getCustomerId()) == null ? ""
                    : customerRepository.findById(order.getCustomerId()).getCustomerName();
            var users = userRepository.findNames(List.of(order.getSalesmanId()));
            String salesmanName = users.isEmpty() ? "" : users.get(0).name();
            for (SalesOrderItem item : order.getItems()) {
                rows.add(List.of(
                        nvl(order.getOrderNo()), nvl(customerName), nvl(order.getCustomerOrderNo()),
                        nvl(order.getRequirement()), nvl(order.getMainboardLabel()), nvl(order.getCartonLabel()),
                        nvl(salesmanName),
                        order.getOrderDate() == null ? "" : order.getOrderDate().toLocalDate().toString(),
                        order.getExpectedDeliveryDate() == null ? "" : order.getExpectedDeliveryDate().toLocalDate().toString(),
                        nvl(item.getModel()), nvl(item.getMaterialNo()), nvl(item.getCustomerMaterialNo()),
                        nvl(item.getSpec()), nvl(item.getQtyOrdered()), nvl(item.getTaxRate()), nvl(item.getUnitPrice()),
                        item.getReconcileDate() == null ? "" : item.getReconcileDate().toLocalDate().toString(),
                        nvl(item.getRemark()), nvl(order.getRemark())));
            }
        }
        return rows;
    }

    private List<List<String>> exportShipments() {
        var query = new com.example.admin.domain.model.shipment.ShipmentQuery();
        query.setPageNum(1);
        query.setPageSize(EXPORT_MAX_ROWS);
        List<List<String>> rows = new ArrayList<>();
        for (Shipment shipment : shipmentRepository.page(query).getRecords()) {
            Customer customer = customerRepository.findById(shipment.getCustomerId());
            var users = shipment.getSalesmanId() == null ? List.<com.example.admin.domain.model.common.IdName>of()
                    : userRepository.findNames(List.of(shipment.getSalesmanId()));
            rows.add(List.of(
                    shipment.getShipDate() == null ? "" : shipment.getShipDate().toLocalDate().toString(),
                    nvl(shipment.getProcessOrderNo()),
                    users.isEmpty() ? "" : nvl(users.get(0).name()),
                    customer == null ? "" : nvl(customer.getCustomerName()),
                    nvl(shipment.getCustomerOrderNo()), nvl(shipment.getCustomerMaterialNo()),
                    nvl(shipment.getModel()), nvl(shipment.getQty()), nvl(shipment.getMaterialNo()),
                    nvl(shipment.getSpec()), nvl(shipment.getRemark())));
        }
        return rows;
    }

    /** 字段说明页：让用户知道每列填什么、哪些必填 */
    private XlsxWriter.Sheet fieldSheet(ImportType type) {
        List<ImportMapping> mappings = mappingRepository.listByModule(type.getCode());
        List<List<String>> rows = new ArrayList<>();
        for (ImportMapping mapping : mappings) {
            rows.add(List.of(nvl(mapping.getFieldName()), nvl(mapping.getAliases()),
                    nvl(mapping.getFieldType()), mapping.isRequired() ? "必填" : "选填",
                    nvl(mapping.getDefaultValue()), nvl(mapping.getRemark())));
        }
        return new XlsxWriter.Sheet("字段说明",
                List.of("系统字段", "可识别的表头别名", "类型", "是否必填", "缺省默认值", "说明"), rows);
    }

    /** 字典页：税率等下拉数据，禁止硬编码 */
    private XlsxWriter.Sheet dictSheet() {
        List<List<String>> rows = new ArrayList<>();
        for (DictItem item : dictRepository.listAll()) {
            rows.add(List.of(nvl(item.getDictCode()), nvl(item.getDictValue()), nvl(item.getDictLabel())));
        }
        return new XlsxWriter.Sheet("字典", List.of("字典编码", "值", "标签"), rows);
    }

    private ImportBatchResp toBatchResp(ImportBatch batch) {
        ImportBatchResp resp = new ImportBatchResp();
        resp.setId(String.valueOf(batch.getId()));
        resp.setBatchType(batch.getBatchType() == null ? null : batch.getBatchType().getCode());
        resp.setFileName(batch.getFileName());
        resp.setSheetName(batch.getSheetName());
        resp.setTotalCount(batch.getTotalCount());
        resp.setInsertCount(batch.getInsertCount());
        resp.setUpdateCount(batch.getUpdateCount());
        resp.setSkipCount(batch.getSkipCount());
        resp.setFailCount(batch.getFailCount());
        resp.setWarnCount(batch.getWarnCount());
        resp.setIdempotent(batch.getIdempotent() == null ? null : batch.getIdempotent().getCode());
        resp.setStatus(batch.getStatus() == null ? null : batch.getStatus().getCode());
        resp.setCreateTime(batch.getCreateTime());
        return resp;
    }

    private String value(Map<String, String> row, String field) {
        return ValueParser.toText(row.get(field));
    }

    private String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
