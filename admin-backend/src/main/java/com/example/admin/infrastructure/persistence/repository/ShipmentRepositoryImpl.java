package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.model.shipment.ShipmentQuery;
import com.example.admin.domain.model.shipment.ShipmentStatus;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.infrastructure.persistence.converter.BizSupportConverter;
import com.example.admin.infrastructure.persistence.mapper.BizShipmentMapper;
import com.example.admin.infrastructure.persistence.po.BizShipment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 出货仓储实现（Infrastructure 层）。
 *
 * <p>幂等判重落在这里：按"加工单号 + 客户订单号 + 料号 + 出货日期 + 数量"查重，
 * 界面录入、Excel 导入、剪贴板粘贴三种入口共用，保证重复导入不产生重复数据。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class ShipmentRepositoryImpl implements ShipmentRepository {

    private final BizShipmentMapper shipmentMapper;
    private final BizSupportConverter converter;

    @Override
    public PageResult<Shipment> page(ShipmentQuery query) {
        LambdaQueryWrapper<BizShipment> wrapper = buildWrapper(query);
        wrapper.isNull(Boolean.TRUE.equals(query.getOnlyOrphan()), BizShipment::getOrderItemId);
        IPage<BizShipment> page = shipmentMapper.selectPage(query.buildPage(), wrapper);
        return toPageResult(page, page.getRecords().stream().map(converter::shipmentToDomain).toList());
    }

    @Override
    public PageResult<Shipment> pageOrphan(ShipmentQuery query) {
        query.setOnlyOrphan(Boolean.TRUE);
        return page(query);
    }

    @Override
    public Shipment findById(Long id) {
        return converter.shipmentToDomain(shipmentMapper.selectById(id));
    }

    @Override
    public Long save(Shipment shipment) {
        BizShipment po = converter.shipmentToPo(shipment);
        shipmentMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(Shipment shipment) {
        shipmentMapper.updateById(converter.shipmentToPo(shipment));
    }

    @Override
    public Shipment findByIdempotentKey(String processOrderNo, String customerOrderNo, String materialNo,
                                        LocalDateTime shipDate, BigDecimal qty) {
        LambdaQueryWrapper<BizShipment> wrapper = new LambdaQueryWrapper<BizShipment>()
                .eq(processOrderNo == null, BizShipment::getProcessOrderNo, null)
                .eq(processOrderNo != null, BizShipment::getProcessOrderNo, processOrderNo)
                .eq(customerOrderNo == null, BizShipment::getCustomerOrderNo, null)
                .eq(customerOrderNo != null, BizShipment::getCustomerOrderNo, customerOrderNo)
                .eq(materialNo == null, BizShipment::getMaterialNo, null)
                .eq(materialNo != null, BizShipment::getMaterialNo, materialNo)
                .eq(qty != null, BizShipment::getQty, qty)
                .last("LIMIT 1");
        if (shipDate != null) {
            LocalDate date = shipDate.toLocalDate();
            wrapper.ge(BizShipment::getShipDate, LocalDateTime.of(date, LocalTime.MIN))
                    .le(BizShipment::getShipDate, LocalDateTime.of(date, LocalTime.MAX));
        }
        List<BizShipment> list = shipmentMapper.selectList(wrapper);
        return CollUtil.isEmpty(list) ? null : converter.shipmentToDomain(list.get(0));
    }

    @Override
    public List<Shipment> listByOrderItem(Long orderItemId) {
        return shipmentMapper.selectList(new LambdaQueryWrapper<BizShipment>()
                        .eq(BizShipment::getOrderItemId, orderItemId)
                        .orderByDesc(BizShipment::getShipDate))
                .stream().map(converter::shipmentToDomain).toList();
    }

    @Override
    public List<Shipment> listByBatch(Long batchId) {
        return shipmentMapper.selectList(new LambdaQueryWrapper<BizShipment>()
                        .eq(BizShipment::getImportBatchId, batchId))
                .stream().map(converter::shipmentToDomain).toList();
    }

    @Override
    public long countToday(Long operatorId) {
        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        Long count = shipmentMapper.selectCount(new LambdaQueryWrapper<BizShipment>()
                .ge(BizShipment::getShipDate, start)
                .le(BizShipment::getShipDate, end)
                .eq(BizShipment::getStatus, ShipmentStatus.NORMAL.getCode())
                .eq(operatorId != null, BizShipment::getCreateBy, operatorId));
        return count == null ? 0L : count;
    }

    @Override
    public List<Shipment> listByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return shipmentMapper.selectList(new LambdaQueryWrapper<BizShipment>()
                        .in(BizShipment::getId, ids))
                .stream().map(converter::shipmentToDomain).toList();
    }

    /**
     * 按查询条件构造通用查询条件（异常池与列表共用）。
     *
     * @param query 查询条件
     * @return MyBatis-Plus 条件构造器
     */
    private LambdaQueryWrapper<BizShipment> buildWrapper(ShipmentQuery query) {
        // 先取值再判空：MyBatisPlus 的条件入参提前求值，直接在 .eq(...) 里调 getCode() 会 NPE
        String statusCode = query.getStatus() == null ? null : query.getStatus().getCode();
        String sourceCode = query.getSource() == null ? null : query.getSource().getCode();
        return new LambdaQueryWrapper<BizShipment>()
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(BizShipment::getProcessOrderNo, query.getKeyword())
                        .or().like(BizShipment::getCustomerOrderNo, query.getKeyword())
                        .or().like(BizShipment::getModel, query.getKeyword())
                        .or().like(BizShipment::getMaterialNo, query.getKeyword()))
                .eq(query.getCustomerId() != null, BizShipment::getCustomerId, query.getCustomerId())
                .eq(query.getSalesmanId() != null, BizShipment::getSalesmanId, query.getSalesmanId())
                .eq(statusCode != null, BizShipment::getStatus, statusCode)
                .eq(sourceCode != null, BizShipment::getSource, sourceCode)
                .eq(query.getOrderItemId() != null, BizShipment::getOrderItemId, query.getOrderItemId())
                .eq(StrUtil.isNotBlank(query.getProcessOrderNo()), BizShipment::getProcessOrderNo, query.getProcessOrderNo())
                .eq(StrUtil.isNotBlank(query.getCustomerOrderNo()), BizShipment::getCustomerOrderNo, query.getCustomerOrderNo())
                .ge(query.getBeginDate() != null, BizShipment::getShipDate, query.getBeginDate())
                .le(query.getEndDate() != null, BizShipment::getShipDate, query.getEndDate())
                .orderByDesc(BizShipment::getShipDate)
                .orderByDesc(BizShipment::getId);
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
        result.setRecords(new ArrayList<>(records));
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
