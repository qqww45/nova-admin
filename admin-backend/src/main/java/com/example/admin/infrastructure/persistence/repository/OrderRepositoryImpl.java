package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.order.ItemStatus;
import com.example.admin.domain.model.order.OrderQuery;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.domain.repository.OrderRepository;
import com.example.admin.infrastructure.persistence.converter.OrderConverter;
import com.example.admin.infrastructure.persistence.mapper.BizSalesOrderItemMapper;
import com.example.admin.infrastructure.persistence.mapper.BizSalesOrderMapper;
import com.example.admin.infrastructure.persistence.po.BizSalesOrder;
import com.example.admin.infrastructure.persistence.po.BizSalesOrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单仓储实现（Infrastructure 层）。
 *
 * <p>以"订单 + 明细"为一个聚合整体读写：删除订单会带走明细，保存明细走批量 upsert，
 * 避免出现"有头无行"或"有行无头"的脏数据。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    /** 报表 / 体检一次最多扫描的订单行数（防止无界全表扫描） */
    private static final long REPORT_MAX_ROWS = 5000L;

    private final BizSalesOrderMapper orderMapper;
    private final BizSalesOrderItemMapper itemMapper;
    private final OrderConverter converter;

    @Override
    public PageResult<SalesOrder> page(OrderQuery query) {
        // 先取值再判空：MyBatisPlus 的条件入参是提前求值的，
        // 写成 .eq(status != null, col, status.getCode()) 会在 status 为 null 时 NPE
        String statusCode = query.getStatus() == null ? null : query.getStatus().getCode();
        LambdaQueryWrapper<BizSalesOrder> wrapper = new LambdaQueryWrapper<BizSalesOrder>()
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(BizSalesOrder::getOrderNo, query.getKeyword())
                        .or().like(BizSalesOrder::getCustomerOrderNo, query.getKeyword()))
                .eq(query.getCustomerId() != null, BizSalesOrder::getCustomerId, query.getCustomerId())
                .eq(query.getSalesmanId() != null, BizSalesOrder::getSalesmanId, query.getSalesmanId())
                .eq(statusCode != null, BizSalesOrder::getStatus, statusCode)
                .eq(StrUtil.isNotBlank(query.getOrderNo()), BizSalesOrder::getOrderNo, query.getOrderNo())
                .eq(StrUtil.isNotBlank(query.getCustomerOrderNo()), BizSalesOrder::getCustomerOrderNo, query.getCustomerOrderNo())
                .ge(query.getBeginDate() != null, BizSalesOrder::getOrderDate, query.getBeginDate())
                .le(query.getEndDate() != null, BizSalesOrder::getOrderDate, query.getEndDate())
                .orderByDesc(BizSalesOrder::getOrderDate)
                .orderByDesc(BizSalesOrder::getId);
        IPage<BizSalesOrder> page = orderMapper.selectPage(query.buildPage(), wrapper);
        List<SalesOrder> records = new ArrayList<>();
        for (BizSalesOrder po : page.getRecords()) {
            SalesOrder order = converter.toDomain(po);
            order.setItems(listItems(po.getId()));
            records.add(order);
        }
        return toPageResult(page, records);
    }

    @Override
    public SalesOrder findById(Long id) {
        if (id == null) {
            return null;
        }
        BizSalesOrder po = orderMapper.selectById(id);
        SalesOrder order = converter.toDomain(po);
        if (order != null) {
            order.setItems(listItems(id));
        }
        return order;
    }

    @Override
    public SalesOrder findByOrderNo(String orderNo) {
        if (StrUtil.isBlank(orderNo)) {
            return null;
        }
        BizSalesOrder po = orderMapper.selectOne(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getOrderNo, orderNo.trim()));
        SalesOrder order = converter.toDomain(po);
        if (order != null) {
            order.setItems(listItems(order.getId()));
        }
        return order;
    }

    @Override
    public boolean existsByOrderNo(String orderNo, Long excludeId) {
        if (StrUtil.isBlank(orderNo)) {
            return false;
        }
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getOrderNo, orderNo.trim())
                .ne(excludeId != null, BizSalesOrder::getId, excludeId));
        return count != null && count > 0;
    }

    @Override
    public Long save(SalesOrder order) {
        BizSalesOrder po = converter.toPo(order);
        orderMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(SalesOrder order) {
        orderMapper.updateById(converter.toPo(order));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        itemMapper.delete(new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, id));
        orderMapper.deleteById(id);
    }

    @Override
    public List<SalesOrderItem> listItems(Long orderId) {
        List<BizSalesOrderItem> list = itemMapper.selectList(new LambdaQueryWrapper<BizSalesOrderItem>()
                .eq(BizSalesOrderItem::getOrderId, orderId)
                .orderByAsc(BizSalesOrderItem::getLineNo));
        return list.stream().map(converter::itemToDomain).toList();
    }

    @Override
    public SalesOrderItem findItem(Long id) {
        return converter.itemToDomain(itemMapper.selectById(id));
    }

    @Override
    public Long saveItem(SalesOrderItem item) {
        BizSalesOrderItem po = converter.itemToPo(item);
        itemMapper.insert(po);
        return po.getId();
    }

    @Override
    public void updateItem(SalesOrderItem item) {
        itemMapper.updateById(converter.itemToPo(item));
    }

    @Override
    public void removeItem(Long id) {
        itemMapper.deleteById(id);
    }

    @Override
    public List<SalesOrder> listByCustomer(Long customerId, int limit) {
        Page<BizSalesOrder> page = new Page<>(1, Math.max(1, limit));
        IPage<BizSalesOrder> result = orderMapper.selectPage(page, new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getCustomerId, customerId)
                .orderByDesc(BizSalesOrder::getOrderDate));
        List<SalesOrder> records = new ArrayList<>();
        for (BizSalesOrder po : result.getRecords()) {
            SalesOrder order = converter.toDomain(po);
            order.setItems(listItems(po.getId()));
            records.add(order);
        }
        return records;
    }

    @Override
    public SalesOrder findByCustomerOrderNo(String customerOrderNo) {
        if (StrUtil.isBlank(customerOrderNo)) {
            return null;
        }
        List<BizSalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getCustomerOrderNo, customerOrderNo.trim()));
        if (orders.isEmpty()) {
            return null;
        }
        SalesOrder order = converter.toDomain(orders.get(0));
        order.setItems(listItems(order.getId()));
        return order;
    }

    @Override
    public List<SalesOrderItem> matchByCustomerOrderAndMaterial(String customerOrderNo, String customerMaterialNo) {
        if (StrUtil.isBlank(customerOrderNo) || StrUtil.isBlank(customerMaterialNo)) {
            return List.of();
        }
        // 明细上没有客户订单号，需经订单头反查：先按客户订单号找订单，再在明细里按客户物料号过滤
        List<BizSalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getCustomerOrderNo, customerOrderNo.trim()));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(BizSalesOrder::getId).toList();
        List<BizSalesOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizSalesOrderItem>()
                .in(BizSalesOrderItem::getOrderId, orderIds)
                .eq(BizSalesOrderItem::getCustomerMaterialNo, customerMaterialNo.trim())
                .eq(BizSalesOrderItem::getStatus, ItemStatus.NORMAL.getCode()));
        return items.stream().map(converter::itemToDomain).toList();
    }

    @Override
    public List<SalesOrder> listForReport(LocalDateTime begin, LocalDateTime end) {
        // 上限保护：报表与体检是运维性质的一次性动作，不允许无界全表扫描
        Page<BizSalesOrder> page = new Page<>(1, REPORT_MAX_ROWS);
        IPage<BizSalesOrder> result = orderMapper.selectPage(page, new LambdaQueryWrapper<BizSalesOrder>()
                .ge(begin != null, BizSalesOrder::getOrderDate, begin)
                .le(end != null, BizSalesOrder::getOrderDate, end)
                .orderByDesc(BizSalesOrder::getOrderDate));
        List<SalesOrder> orders = new ArrayList<>();
        for (BizSalesOrder po : result.getRecords()) {
            SalesOrder order = converter.toDomain(po);
            order.setItems(listItems(po.getId()));
            orders.add(order);
        }
        return orders;
    }

    @Override
    public List<SalesOrderItem> matchByCustomerAndMaterial(Long customerId, String materialNo) {
        if (customerId == null || StrUtil.isBlank(materialNo)) {
            return List.of();
        }
        List<BizSalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getCustomerId, customerId));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(BizSalesOrder::getId).toList();
        List<BizSalesOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizSalesOrderItem>()
                .in(BizSalesOrderItem::getOrderId, orderIds)
                .eq(BizSalesOrderItem::getMaterialNo, materialNo.trim())
                .eq(BizSalesOrderItem::getStatus, ItemStatus.NORMAL.getCode()));
        return items.stream().map(converter::itemToDomain).toList();
    }

    /**
     * 组装统一分页响应。
     *
     * @param page    分页对象
     * @param records 已转换的记录
     * @param <T>     记录类型
     * @return 分页响应
     */
    private <T> PageResult<T> toPageResult(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
