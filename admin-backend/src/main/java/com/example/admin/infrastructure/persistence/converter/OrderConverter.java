package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.order.ItemStatus;
import com.example.admin.domain.model.order.OrderStatus;
import com.example.admin.domain.model.order.ReconcileStatus;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.infrastructure.persistence.po.BizSalesOrder;
import com.example.admin.infrastructure.persistence.po.BizSalesOrderItem;
import org.springframework.stereotype.Component;

/**
 * 订单聚合 PO ⇄ 领域模型转换器。
 *
 * <p>枚举字段在库里存编码，转换时做一次解析；解析不到的编码按 null 处理，
 * 由上层给出明确提示，不让脏数据静默通过。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class OrderConverter {

    /**
     * PO → 领域模型（不含明细）。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public SalesOrder toDomain(BizSalesOrder po) {
        if (po == null) {
            return null;
        }
        SalesOrder order = new SalesOrder();
        BeanUtil.copyProperties(po, order);
        order.setStatus(OrderStatus.ofCode(po.getStatus()));
        return order;
    }

    /**
     * 领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizSalesOrder toPo(SalesOrder domain) {
        if (domain == null) {
            return null;
        }
        BizSalesOrder po = new BizSalesOrder();
        BeanUtil.copyProperties(domain, po);
        po.setStatus(domain.getStatus() == null ? OrderStatus.DRAFT.getCode() : domain.getStatus().getCode());
        return po;
    }

    /**
     * 明细 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public SalesOrderItem itemToDomain(BizSalesOrderItem po) {
        if (po == null) {
            return null;
        }
        SalesOrderItem item = new SalesOrderItem();
        BeanUtil.copyProperties(po, item);
        item.setStatus(ItemStatus.ofCode(po.getStatus()));
        item.setReconcileStatus(ReconcileStatus.ofCode(po.getReconcileStatus()));
        return item;
    }

    /**
     * 明细领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizSalesOrderItem itemToPo(SalesOrderItem domain) {
        if (domain == null) {
            return null;
        }
        BizSalesOrderItem po = new BizSalesOrderItem();
        BeanUtil.copyProperties(domain, po);
        po.setStatus(domain.getStatus() == null ? ItemStatus.NORMAL.getCode() : domain.getStatus().getCode());
        po.setReconcileStatus(domain.getReconcileStatus() == null
                ? ReconcileStatus.UNRECONCILED.getCode() : domain.getReconcileStatus().getCode());
        return po;
    }
}
