package com.example.admin.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.application.assembler.BizAssembler;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.model.shipment.ShipmentQuery;
import com.example.admin.domain.model.shipment.ShipmentSource;
import com.example.admin.domain.model.shipment.ShipmentStatus;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.OperationLogRepository;
import com.example.admin.domain.repository.OrderRepository;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.service.AmountCalculator;
import com.example.admin.domain.service.QuantityPolicy;
import com.example.admin.application.service.ShipmentAppService;
import com.example.admin.interfaces.dto.req.ShipmentPageQuery;
import com.example.admin.interfaces.dto.req.ShipmentRowReq;
import com.example.admin.interfaces.dto.req.ShipmentSaveReq;
import com.example.admin.interfaces.dto.resp.BatchLineResult;
import com.example.admin.interfaces.dto.resp.ShipmentBatchResultResp;
import com.example.admin.interfaces.dto.resp.ShipmentResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 出货应用服务实现（本系统最高频的写操作）。
 *
 * <p>核心规则（需求 4.1 / 4.3 / 附录A A.4）：
 * <ul>
 *     <li>新增必须 {@code 0 < qty ≤ qty_open}，超额拦截（导入与界面同一口径）；</li>
 *     <li>保存成功后回写明细出货数并重算订单状态，同一事务；</li>
 *     <li>禁止物理删除，作废时回写扣减并记录原因；</li>
 *     <li>匹配订单明细：客户订单号+客户物料号 → 客户订单号+型号 → 客户+料号 → 客户+型号，
 *         命中多条取"未出货数最大"的；全部未命中则记为无源出货，进异常池。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentAppServiceImpl implements ShipmentAppService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OperationLogRepository operationLogRepository;
    private final BizUserRepository userRepository;
    private final BizAssembler assembler;

    @Override
    public PageResult<ShipmentResp> page(ShipmentPageQuery query) {
        ShipmentQuery domainQuery = toDomainQuery(query);
        PageResult<Shipment> page = Boolean.TRUE.equals(query.getOnlyOrphan())
                ? shipmentRepository.pageOrphan(domainQuery)
                : shipmentRepository.page(domainQuery);
        List<ShipmentResp> records = new ArrayList<>();
        for (Shipment shipment : page.getRecords()) {
            records.add(toResp(shipment));
        }
        return toPage(page, records);
    }

    @Override
    public ShipmentResp detail(String id) {
        Shipment shipment = shipmentRepository.findById(IdParser.parseId(id));
        if (shipment == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "出货记录不存在");
        }
        return toResp(shipment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShipmentBatchResultResp create(ShipmentSaveReq req) {
        List<ShipmentRowReq> rows = req.getRows();
        ShipmentSource source = ShipmentSource.ofCode(req.getSource());
        if (source == null) {
            source = ShipmentSource.MANUAL;
        }
        List<BatchLineResult> results = new ArrayList<>();
        int success = 0;
        for (int i = 0; i < rows.size(); i++) {
            int lineNo = i + 1;
            try {
                Shipment saved = saveOne(rows.get(i), source);
                results.add(BatchLineResult.success(lineNo, String.valueOf(saved.getId())));
                success++;
            } catch (BusinessException e) {
                // 单行失败不影响其它行，原因原样带回供就地修正
                results.add(BatchLineResult.fail(lineNo, e.getMessage(), null, null));
            } catch (Exception e) {
                log.error("保存出货失败，第 {} 行", lineNo, e);
                results.add(BatchLineResult.fail(lineNo, "系统异常：" + e.getMessage(), null, null));
            }
        }
        ShipmentBatchResultResp resp = new ShipmentBatchResultResp();
        resp.setTotal(rows.size());
        resp.setSuccessCount(success);
        resp.setFailCount(rows.size() - success);
        resp.setResults(results);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidShipment(String id, String reason) {
        Long shipmentId = IdParser.parseId(id);
        Shipment shipment = shipmentRepository.findById(shipmentId);
        if (shipment == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "出货记录不存在");
        }
        if (shipment.isVoid()) {
            throw new BusinessException("该出货记录已作废，无需重复操作");
        }
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("作废出货属于敏感操作，请填写原因");
        }
        String before = describe(shipment);
        SalesOrderItem item = shipment.getOrderItemId() == null
                ? null : orderRepository.findItem(shipment.getOrderItemId());
        if (item != null) {
            item.revertShipment(shipment.getQty());
            orderRepository.updateItem(item);
            refreshOrderStatus(item.getOrderId());
        }
        shipment.void_(reason);
        shipmentRepository.update(shipment);

        operationLogRepository.save(OperationLog.of("SHIPMENT", String.valueOf(shipmentId), "VOID",
                before, describe(shipment), reason, shipment.getUpdateBy(), LocalDateTime.now()));
    }

    @Override
    public List<ShipmentResp> listByItem(String itemId) {
        return shipmentRepository.listByOrderItem(IdParser.parseId(itemId)).stream()
                .map(this::toResp).toList();
    }

    @Override
    public long countToday() {
        return shipmentRepository.countToday(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkToItem(String id, String orderItemId) {
        Long shipmentId = IdParser.parseId(id);
        Shipment shipment = shipmentRepository.findById(shipmentId);
        if (shipment == null) {
            throw new BusinessException("出货记录不存在：" + id);
        }
        if (!shipment.isOrphan()) {
            throw new BusinessException("该出货已关联订单明细，无需补挂");
        }
        SalesOrderItem item = orderRepository.findItem(IdParser.parseId(orderItemId));
        if (item == null) {
            throw new BusinessException("订单明细不存在：" + orderItemId);
        }
        if (AmountCalculator.nz(item.getQtyOpen()).compareTo(shipment.getQty()) < 0) {
            throw new BusinessException(
                    "该明细未出货数仅剩 " + AmountCalculator.nz(item.getQtyOpen())
                            + "，不足本次出货数 " + shipment.getQty() + "，请先确认数量或改挂其它明细");
        }

        shipment.linkTo(item.getId());
        item.applyShipment(shipment.getQty());
        shipment.setOrderNo(orderNoOf(item.getOrderId()));
        shipment.snapshotAmount(item.getUnitPrice(), item.getTaxRate());

        shipmentRepository.update(shipment);
        orderRepository.updateItem(item);
        refreshOrderStatus(item.getOrderId());

        operationLogRepository.save(OperationLog.of("SHIPMENT", String.valueOf(shipmentId), "LINK",
                null, "补挂订单明细 " + item.getId(), null, null, LocalDateTime.now()));
    }

    /* ---------------- 私有辅助 ---------------- */

    /**
     * 保存单行出货（校验 → 落库 → 回写明细 → 重算订单状态）。
     *
     * @param row    行数据
     * @param source 来源
     * @return 已保存的出货记录
     */
    private Shipment saveOne(ShipmentRowReq row, ShipmentSource source) {
        if (StrUtil.isBlank(row.getModel())) {
            throw new BusinessException("型号不能为空");
        }
        if (row.getQty() == null || row.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("出货数量必须大于 0");
        }
        // 客户与业务员均为必填（biz_shipment 两列都是 NOT NULL，导入映射同样 required=1）。
        // 用 parseIdOrNull 而不是 parseId：后者对空串直接抛"ID不能为空"，
        // 既掩盖了真实原因，也让下面"填了才校验存在性"的分支成为永远不成立死的代码
        Long customerId = IdParser.parseIdOrNull(row.getCustomerId());
        if (customerId == null) {
            throw new BusinessException("客户不能为空");
        }
        if (customerRepository.findById(customerId) == null) {
            throw new BusinessException("客户不存在：" + row.getCustomerId());
        }
        Long salesmanId = IdParser.parseIdOrNull(row.getSalesmanId());
        if (salesmanId == null) {
            throw new BusinessException("业务员不能为空");
        }
        if (userRepository.findNames(List.of(salesmanId)).isEmpty()) {
            throw new BusinessException("业务员不存在：" + row.getSalesmanId());
        }

        Shipment shipment = new Shipment();
        shipment.setShipDate(row.getShipDate() == null
                ? LocalDateTime.of(LocalDate.now(), LocalTime.MIN) : row.getShipDate());
        shipment.setProcessOrderNo(row.getProcessOrderNo());
        shipment.setSalesmanId(salesmanId);
        shipment.setCustomerId(customerId);
        shipment.setCustomerOrderNo(row.getCustomerOrderNo());
        shipment.setCustomerMaterialNo(row.getCustomerMaterialNo());
        shipment.setModel(row.getModel());
        shipment.setMaterialNo(row.getMaterialNo());
        shipment.setSpec(row.getSpec());
        shipment.setQty(row.getQty());
        shipment.setStatus(ShipmentStatus.NORMAL);
        shipment.setSource(source);
        shipment.setRemark(row.getRemark());

        // 显式指定明细时优先用指定值；否则按顺序自动匹配
        SalesOrderItem item = StrUtil.isNotBlank(row.getOrderItemId())
                ? orderRepository.findItem(IdParser.parseId(row.getOrderItemId()))
                : matchItem(shipment);

        if (item == null) {
            // 无源出货：金额无法计算，留空并进异常池，不阻断本次录入
            shipment.setOrderItemId(null);
        } else {
            item.applyShipment(shipment.getQty());
            shipment.setOrderItemId(item.getId());
            shipment.setOrderNo(orderNoOf(item.getOrderId()));
            shipment.snapshotAmount(item.getUnitPrice(), item.getTaxRate());
        }

        Long id = shipmentRepository.save(shipment);
        shipment.setId(id);

        if (item != null) {
            orderRepository.updateItem(item);
            refreshOrderStatus(item.getOrderId());
        }
        operationLogRepository.save(OperationLog.of("SHIPMENT", String.valueOf(id), "SHIP",
                null, describe(shipment), null, salesmanId, LocalDateTime.now()));
        return shipment;
    }

    /**
     * 按附录A A.4 的优先级匹配订单明细。
     *
     * @param shipment 出货数据
     * @return 命中的明细；未命中返回 null
     */
    private SalesOrderItem matchItem(Shipment shipment) {
        List<SalesOrderItem> candidates = new ArrayList<>();

        // 优先级一：客户订单号 + 客户物料号
        if (StrUtil.isNotBlank(shipment.getCustomerOrderNo()) && StrUtil.isNotBlank(shipment.getCustomerMaterialNo())) {
            candidates.addAll(orderRepository.matchByCustomerOrderAndMaterial(
                    shipment.getCustomerOrderNo(), shipment.getCustomerMaterialNo()));
        }
        // 优先级二：客户订单号 + 型号
        if (candidates.isEmpty() && StrUtil.isNotBlank(shipment.getCustomerOrderNo())) {
            SalesOrder order = findOrderByCustomerOrderNo(shipment.getCustomerOrderNo());
            if (order != null) {
                candidates.addAll(filterByModel(order.getItems(), shipment.getModel()));
            }
        }
        // 优先级三：客户 + 料号
        if (candidates.isEmpty() && shipment.getCustomerId() != null && StrUtil.isNotBlank(shipment.getMaterialNo())) {
            candidates.addAll(orderRepository.matchByCustomerAndMaterial(
                    shipment.getCustomerId(), shipment.getMaterialNo()));
        }
        // 优先级四：客户 + 型号
        if (candidates.isEmpty() && shipment.getCustomerId() != null) {
            candidates.addAll(filterByModel(listItemsOfCustomer(shipment.getCustomerId()), shipment.getModel()));
        }
        // 命中多条：取未出货数最大的那条（最可能是本次要出的）
        return candidates.stream()
                .filter(i -> i.getStatus() != com.example.admin.domain.model.order.ItemStatus.CANCELED)
                .filter(i -> AmountCalculator.nz(i.getQtyOpen()).compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(i -> AmountCalculator.nz(i.getQtyOpen())))
                .orElse(null);
    }

    private SalesOrder findOrderByCustomerOrderNo(String customerOrderNo) {
        return orderRepository.findByCustomerOrderNo(customerOrderNo);
    }

    private List<SalesOrderItem> listItemsOfCustomer(Long customerId) {
        List<SalesOrderItem> items = new ArrayList<>();
        for (SalesOrder order : orderRepository.listByCustomer(customerId, 100)) {
            items.addAll(order.getItems());
        }
        return items;
    }

    private List<SalesOrderItem> filterByModel(List<SalesOrderItem> items, String model) {
        if (StrUtil.isBlank(model)) {
            return List.of();
        }
        return items.stream().filter(i -> model.trim().equalsIgnoreCase(i.getModel())).toList();
    }

    private void refreshOrderStatus(Long orderId) {
        if (orderId == null) {
            return;
        }
        SalesOrder order = orderRepository.findById(orderId);
        if (order == null) {
            return;
        }
        order.refreshStatus();
        orderRepository.update(order);
    }

    private String orderNoOf(Long orderId) {
        SalesOrder order = orderRepository.findById(orderId);
        return order == null ? null : order.getOrderNo();
    }

    private ShipmentResp toResp(Shipment shipment) {
        String customerName = null;
        if (shipment.getCustomerId() != null) {
            Customer customer = customerRepository.findById(shipment.getCustomerId());
            if (customer != null) {
                customerName = customer.getCustomerName();
            }
        }
        String salesmanName = null;
        if (shipment.getSalesmanId() != null) {
            var users = userRepository.findNames(List.of(shipment.getSalesmanId()));
            if (!users.isEmpty()) {
                salesmanName = users.get(0).name();
            }
        }
        String orderNo = null;
        if (shipment.getOrderItemId() != null) {
            SalesOrderItem item = orderRepository.findItem(shipment.getOrderItemId());
            if (item != null) {
                orderNo = orderNoOf(item.getOrderId());
            }
        }
        return assembler.toShipmentResp(shipment, customerName, salesmanName, orderNo);
    }

    /** 操作日志用的可读快照 */
    private String describe(Shipment shipment) {
        return "出货[" + (shipment.getShipDate() == null ? "" : shipment.getShipDate().toLocalDate())
                + " " + shipment.getModel() + " x "
                + (shipment.getQty() == null ? "" : shipment.getQty().stripTrailingZeros().toPlainString())
                + " " + shipment.getStatus() + "]";
    }

    private ShipmentQuery toDomainQuery(ShipmentPageQuery query) {
        ShipmentQuery domainQuery = new ShipmentQuery();
        domainQuery.setPageNum(query.getPageNum());
        domainQuery.setPageSize(query.getPageSize());
        domainQuery.setSortBy(query.getSortBy());
        domainQuery.setSortOrder(query.getSortOrder());
        domainQuery.setKeyword(query.getKeyword());
        domainQuery.setCustomerId(IdParser.parseIdOrNull(query.getCustomerId()));
        domainQuery.setSalesmanId(IdParser.parseIdOrNull(query.getSalesmanId()));
        domainQuery.setProcessOrderNo(query.getProcessOrderNo());
        domainQuery.setCustomerOrderNo(query.getCustomerOrderNo());
        domainQuery.setBeginDate(query.getBeginDate());
        domainQuery.setEndDate(query.getEndDate());
        domainQuery.setStatus(ShipmentStatus.ofCode(query.getStatus()));
        domainQuery.setOnlyOrphan(query.getOnlyOrphan());
        return domainQuery;
    }

    private <T> PageResult<T> toPage(PageResult<?> source, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(source.getTotal());
        result.setPageNum(source.getPageNum());
        result.setPageSize(source.getPageSize());
        result.setPages(source.getPages());
        return result;
    }
}
