package com.example.admin.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.application.assembler.BizAssembler;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.order.ItemStatus;
import com.example.admin.domain.model.order.OrderQuery;
import com.example.admin.domain.model.order.OrderStatus;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.OrderRepository;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.service.AmountCalculator;
import com.example.admin.domain.service.QuantityPolicy;
import com.example.admin.application.service.OrderAppService;
import com.example.admin.interfaces.dto.req.ItemMatchQuery;
import com.example.admin.interfaces.dto.req.OrderItemSaveReq;
import com.example.admin.interfaces.dto.req.OrderPageQuery;
import com.example.admin.interfaces.dto.req.OrderSaveReq;
import com.example.admin.interfaces.dto.resp.OrderDetailResp;
import com.example.admin.interfaces.dto.resp.OrderItemResp;
import com.example.admin.interfaces.dto.resp.OrderResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单应用服务实现。
 *
 * <p>关键规则（需求 4 / 6.2）：
 * <ul>
 *     <li>订单号生成后只读；</li>
 *     <li>明细金额与未出货数一律由 {@link SalesOrderItem#recalculate()} 计算，前端传来的金额字段忽略；</li>
 *     <li>删除已有出货的明细会被拦截，只能取消；</li>
 *     <li>取消订单前须先作废其下全部出货；</li>
 *     <li>状态由 {@code refreshStatus()} 重算，不允许前端直接改。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderAppServiceImpl implements OrderAppService {

    private static final DateTimeFormatter ORDER_NO_PREFIX = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ShipmentRepository shipmentRepository;
    private final BizUserRepository userRepository;
    private final BizAssembler assembler;

    @Override
    public PageResult<OrderResp> page(OrderPageQuery query) {
        OrderQuery domainQuery = new OrderQuery();
        domainQuery.setPageNum(query.getPageNum());
        domainQuery.setPageSize(query.getPageSize());
        domainQuery.setSortBy(query.getSortBy());
        domainQuery.setSortOrder(query.getSortOrder());
        domainQuery.setKeyword(query.getKeyword());
        domainQuery.setCustomerId(IdParser.parseIdOrNull(query.getCustomerId()));
        domainQuery.setSalesmanId(IdParser.parseIdOrNull(query.getSalesmanId()));
        domainQuery.setStatus(OrderStatus.ofCode(query.getStatus()));
        domainQuery.setBeginDate(query.getBeginDate());
        domainQuery.setEndDate(query.getEndDate());
        domainQuery.setOnlyOpen(query.getOnlyOpen());

        PageResult<SalesOrder> page = orderRepository.page(domainQuery);
        List<OrderResp> records = new ArrayList<>();
        for (SalesOrder order : page.getRecords()) {
            records.add(assembler.toOrderResp(order, customerName(order.getCustomerId()), salesmanName(order.getSalesmanId())));
        }
        return toPage(page, records);
    }

    @Override
    public OrderDetailResp detail(String id) {
        SalesOrder order = requireOrder(id);
        return assembler.toOrderDetail(order, customerName(order.getCustomerId()), salesmanName(order.getSalesmanId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(OrderSaveReq req) {
        Long customerId = IdParser.parseId(req.getCustomerId());
        if (customerRepository.findById(customerId) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在或已被删除");
        }
        String orderNo = StrUtil.isBlank(req.getOrderNo()) ? generateOrderNo() : req.getOrderNo().trim();
        if (orderRepository.existsByOrderNo(orderNo, null)) {
            throw new BusinessException(ResultCode.DATA_EXIST, "订单号已存在：" + orderNo);
        }
        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNo);
        order.setCustomerId(customerId);
        order.setCustomerOrderNo(req.getCustomerOrderNo());
        order.setRequirement(req.getRequirement());
        order.setMainboardLabel(req.getMainboardLabel());
        order.setCartonLabel(req.getCartonLabel());
        order.setSalesmanId(IdParser.parseId(req.getSalesmanId()));
        order.setOrderDate(req.getOrderDate() == null ? LocalDateTime.now() : req.getOrderDate());
        order.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        order.setStatus(OrderStatus.DRAFT);
        order.setCurrency(StrUtil.isBlank(req.getCurrency()) ? "CNY" : req.getCurrency());
        order.setSettlementMethod(req.getSettlementMethod());
        order.setDeliveryMethod(req.getDeliveryMethod());
        order.setRemark(req.getRemark());

        Long orderId = orderRepository.save(order);
        order.setId(orderId);
        saveItems(order, req.getItems());
        return String.valueOf(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OrderSaveReq req) {
        SalesOrder order = requireOrder(req.getId());
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessException("订单已取消，不能修改");
        }
        Long customerId = IdParser.parseId(req.getCustomerId());
        if (customerRepository.findById(customerId) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在或已被删除");
        }
        order.setCustomerId(customerId);
        order.setCustomerOrderNo(req.getCustomerOrderNo());
        order.setRequirement(req.getRequirement());
        order.setMainboardLabel(req.getMainboardLabel());
        order.setCartonLabel(req.getCartonLabel());
        order.setSalesmanId(IdParser.parseId(req.getSalesmanId()));
        if (req.getOrderDate() != null) {
            order.setOrderDate(req.getOrderDate());
        }
        order.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        order.setCurrency(req.getCurrency());
        order.setSettlementMethod(req.getSettlementMethod());
        order.setDeliveryMethod(req.getDeliveryMethod());
        order.setRemark(req.getRemark());
        orderRepository.update(order);

        saveItems(order, req.getItems());
        order.setItems(orderRepository.listItems(order.getId()));
        order.refreshStatus();
        orderRepository.update(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<String> ids) {
        for (String id : ids) {
            SalesOrder order = requireOrder(id);
            if (order.getStatus() == OrderStatus.CANCELED) {
                throw new BusinessException("订单 " + order.getOrderNo() + " 已取消，无需删除");
            }
            // 已有出货的订单禁止删除（出货记录要留痕）
            for (SalesOrderItem item : order.getItems()) {
                if (AmountCalculator.nz(item.getQtyShipped()).compareTo(BigDecimal.ZERO) > 0) {
                    throw new BusinessException("订单 " + order.getOrderNo()
                            + " 已存在出货记录，不允许删除，请改为取消订单");
                }
            }
            orderRepository.remove(order.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(String id) {
        SalesOrder order = requireOrder(id);
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessException("订单已取消，不能确认");
        }
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.PARTIAL_SHIPPED) {
            throw new BusinessException("订单已在出货流程中，无需确认");
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.update(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id, String reason) {
        SalesOrder order = requireOrder(id);
        if (order.getStatus() == OrderStatus.CANCELED) {
            return;
        }
        if (!order.canCancel()) {
            throw new BusinessException("订单 " + order.getOrderNo()
                    + " 存在已出货的明细，请先将对应出货记录作废，再取消订单");
        }
        order.setStatus(OrderStatus.CANCELED);
        for (SalesOrderItem item : order.getItems()) {
            item.setStatus(ItemStatus.CANCELED);
            orderRepository.updateItem(item);
        }
        orderRepository.update(order);
        if (StrUtil.isNotBlank(reason)) {
            order.setRemark((order.getRemark() == null ? "" : order.getRemark() + "；") + "取消原因：" + reason);
            orderRepository.update(order);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcile(List<String> itemIds, LocalDateTime date) {
        if (CollUtil.isEmpty(itemIds)) {
            throw new BusinessException("请选择要对账的明细");
        }
        for (String itemId : itemIds) {
            SalesOrderItem item = orderRepository.findItem(IdParser.parseId(itemId));
            if (item == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "订单明细不存在");
            }
            item.reconcile(date);
            orderRepository.updateItem(item);
        }
    }

    @Override
    public String generateOrderNo() {
        // 订单号：SO + 日期 + 4 位序号（序号按当日最大号 +1，同库并发下由唯一索引兜底）
        String prefix = "SO" + LocalDateTime.now().format(ORDER_NO_PREFIX);
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + String.format("%04d", (int) (Math.random() * 9000) + 1000);
            if (!orderRepository.existsByOrderNo(candidate, null)) {
                return candidate;
            }
        }
        return prefix + System.currentTimeMillis() % 100000L;
    }

    @Override
    public List<OrderItemResp> matchItems(ItemMatchQuery query) {
        List<SalesOrderItem> candidates = new ArrayList<>();

        // 优先级一：客户订单号 + 客户物料号（最精确，客户 PO 上两个都有）
        if (StrUtil.isNotBlank(query.getCustomerOrderNo()) && StrUtil.isNotBlank(query.getCustomerMaterialNo())) {
            candidates.addAll(orderRepository.matchByCustomerOrderAndMaterial(
                    query.getCustomerOrderNo(), query.getCustomerMaterialNo()));
        }
        // 优先级二：客户订单号 + 型号（客户没写物料号时）
        if (candidates.isEmpty() && StrUtil.isNotBlank(query.getCustomerOrderNo())
                && StrUtil.isNotBlank(query.getModel())) {
            SalesOrder order = orderRepository.findByCustomerOrderNo(query.getCustomerOrderNo());
            if (order != null) {
                String model = query.getModel().trim();
                candidates.addAll(order.getItems().stream()
                        .filter(item -> model.equalsIgnoreCase(item.getModel()))
                        .toList());
            }
        }
        // 优先级三：客户 + 料号（连客户订单号都没有时，退到最宽的条件）
        if (candidates.isEmpty() && StrUtil.isNotBlank(query.getCustomerId())
                && StrUtil.isNotBlank(query.getMaterialNo())) {
            candidates.addAll(orderRepository.matchByCustomerAndMaterial(
                    IdParser.parseIdOrNull(query.getCustomerId()), query.getMaterialNo()));
        }

        // 出完货的明细不再带出，避免用户选中后被数量校验挡回
        return candidates.stream()
                .filter(item -> AmountCalculator.nz(item.getQtyOpen()).compareTo(BigDecimal.ZERO) > 0)
                .map(assembler::toItemResp)
                .toList();
    }

    /* ---------------- 私有辅助 ---------------- */

    /**
     * 整单保存明细：新增行插入、已有行更新、缺失行删除（已出货的行只能取消不能删除）。
     *
     * @param order 订单（已落库）
     * @param items 提交的明细
     */
    private void saveItems(SalesOrder order, List<OrderItemSaveReq> items) {
        if (CollUtil.isEmpty(items)) {
            throw new BusinessException("订单明细不能为空");
        }
        List<SalesOrderItem> existing = orderRepository.listItems(order.getId());
        List<Long> keepIds = new ArrayList<>();
        int lineNo = 1;
        for (OrderItemSaveReq req : items) {
            QuantityPolicy.validateOrderQty(req.getQtyOrdered(), "型号 " + req.getModel());
            SalesOrderItem item;
            if (StrUtil.isNotBlank(req.getId())) {
                item = orderRepository.findItem(IdParser.parseId(req.getId()));
                if (item == null || !order.getId().equals(item.getOrderId())) {
                    throw new BusinessException(ResultCode.DATA_NOT_FOUND, "订单明细不存在：" + req.getId());
                }
            } else {
                item = new SalesOrderItem();
                item.setOrderId(order.getId());
                item.setQtyShipped(BigDecimal.ZERO);
                item.setStatus(ItemStatus.NORMAL);
            }
            item.setLineNo(lineNo++);
            item.setModel(req.getModel());
            item.setMaterialNo(req.getMaterialNo());
            item.setCustomerMaterialNo(req.getCustomerMaterialNo());
            item.setSpec(req.getSpec());
            item.setQtyOrdered(req.getQtyOrdered());
            item.setTaxRate(req.getTaxRate());
            item.setUnitPrice(req.getUnitPrice());
            item.setRemark(req.getRemark());
            item.reconcile(req.getReconcileDate());
            // 金额与未出货数一律服务端计算
            item.recalculate();

            if (item.getId() == null) {
                Long id = orderRepository.saveItem(item);
                item.setId(id);
            } else {
                orderRepository.updateItem(item);
            }
            keepIds.add(item.getId());
        }
        // 删除本次未提交的旧明细（已有出货的禁止删除）
        for (SalesOrderItem old : existing) {
            if (keepIds.contains(old.getId())) {
                continue;
            }
            if (!com.example.admin.domain.service.OrderStatusPolicy.canDeleteItem(old)) {
                throw new BusinessException("明细【" + old.getModel()
                        + "】已有出货记录，不允许删除，只能取消该行");
            }
            orderRepository.removeItem(old.getId());
        }
    }

    private SalesOrder requireOrder(String id) {
        SalesOrder order = orderRepository.findById(IdParser.parseId(id));
        if (order == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "订单不存在或已被删除");
        }
        return order;
    }

    private String customerName(Long customerId) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId);
        return customer == null ? null : customer.getCustomerName();
    }

    private String salesmanName(Long userId) {
        if (userId == null) {
            return null;
        }
        var user = userRepository.findNames(List.of(userId));
        return user.isEmpty() ? null : user.get(0).name();
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
