package com.example.admin.application.assembler;

import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.customer.CustomerContact;
import com.example.admin.domain.model.customer.CustomerInvoice;
import com.example.admin.domain.model.order.ItemStatus;
import com.example.admin.domain.model.order.OrderStatus;
import com.example.admin.domain.model.order.ReconcileStatus;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.service.AmountCalculator;
import com.example.admin.interfaces.dto.resp.CustomerContactResp;
import com.example.admin.interfaces.dto.resp.CustomerDetailResp;
import com.example.admin.interfaces.dto.resp.CustomerInvoiceResp;
import com.example.admin.interfaces.dto.resp.CustomerResp;
import com.example.admin.interfaces.dto.resp.OrderBriefResp;
import com.example.admin.interfaces.dto.resp.OrderDetailResp;
import com.example.admin.interfaces.dto.resp.OrderItemResp;
import com.example.admin.interfaces.dto.resp.OrderResp;
import com.example.admin.interfaces.dto.resp.ShipmentResp;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 客户-订单-出货 领域模型 → 响应 DTO 的装配器。
 *
 * <p>只做"翻译 + 补标签"（状态码 → 中文标签、税率 → 百分比标签），不做任何业务判断；
 * 金额与数量仍由领域层算好后原样带出，避免展示层二次计算导致与库里对不上。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class BizAssembler {

    /**
     * 客户 → 列表项。
     *
     * @param customer 客户
     * @return 列表项
     */
    public CustomerResp toCustomerResp(Customer customer) {
        CustomerResp resp = new CustomerResp();
        resp.setId(str(customer.getId()));
        resp.setCustomerName(customer.getCustomerName());
        resp.setCustomerCode(customer.getCustomerCode());
        resp.setAddress(customer.getAddress());
        resp.setRemark(customer.getRemark());
        resp.setStatus(customer.getStatus());
        resp.setStatusLabel(customer.isEnabled() ? "启用" : "停用");
        CustomerContact contact = customer.defaultContact();
        if (contact != null) {
            resp.setDefaultContactName(contact.getContactName());
            resp.setDefaultContactPhone(contact.getMobile() != null ? contact.getMobile() : contact.getPhone());
        }
        resp.setOrderCount(customer.getOrderCount());
        resp.setCreateTime(customer.getCreateTime());
        resp.setUpdateTime(customer.getUpdateTime());
        return resp;
    }

    /**
     * 客户 → 详情。
     *
     * @param customer   客户
     * @param orders     关联订单
     * @param orderCount 订单数
     * @return 详情
     */
    public CustomerDetailResp toCustomerDetail(Customer customer, List<SalesOrder> orders, Integer orderCount) {
        CustomerDetailResp resp = new CustomerDetailResp();
        resp.setId(str(customer.getId()));
        resp.setCustomerName(customer.getCustomerName());
        resp.setCustomerCode(customer.getCustomerCode());
        resp.setAddress(customer.getAddress());
        resp.setRemark(customer.getRemark());
        resp.setStatus(customer.getStatus());
        resp.setContacts(customer.getContacts().stream().map(this::toContactResp).toList());
        resp.setInvoices(customer.getInvoices().stream().map(this::toInvoiceResp).toList());
        resp.setOrders(orders.stream().map(this::toOrderBrief).toList());
        resp.setOrderCount(orderCount);
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrder order : orders) {
            total = total.add(order.totalAmountTaxIncluded());
        }
        resp.setTotalAmount(total.setScale(AmountCalculator.MONEY_SCALE, RoundingMode.HALF_UP));
        return resp;
    }

    /**
     * 联系人 → 响应。
     *
     * @param contact 联系人
     * @return 响应
     */
    public CustomerContactResp toContactResp(CustomerContact contact) {
        CustomerContactResp resp = new CustomerContactResp();
        resp.setId(str(contact.getId()));
        resp.setCustomerId(str(contact.getCustomerId()));
        resp.setContactName(contact.getContactName());
        resp.setTitle(contact.getTitle());
        resp.setPhone(contact.getPhone());
        resp.setMobile(contact.getMobile());
        resp.setEmail(contact.getEmail());
        resp.setIsDefault(contact.getIsDefault());
        resp.setStatus(contact.getStatus());
        resp.setRemark(contact.getRemark());
        return resp;
    }

    /**
     * 开票信息 → 响应。
     *
     * @param invoice 开票信息
     * @return 响应
     */
    public CustomerInvoiceResp toInvoiceResp(CustomerInvoice invoice) {
        CustomerInvoiceResp resp = new CustomerInvoiceResp();
        resp.setId(str(invoice.getId()));
        resp.setCustomerId(str(invoice.getCustomerId()));
        resp.setInvoiceTitle(invoice.getInvoiceTitle());
        resp.setTaxNo(invoice.getTaxNo());
        resp.setBankName(invoice.getBankName());
        resp.setBankAccount(invoice.getBankAccount());
        resp.setInvoiceAddress(invoice.getInvoiceAddress());
        resp.setInvoicePhone(invoice.getInvoicePhone());
        resp.setInvoiceType(invoice.getInvoiceType());
        resp.setInvoiceTypeLabel(invoiceTypeLabel(invoice.getInvoiceType()));
        resp.setIsDefault(invoice.getIsDefault());
        resp.setStatus(invoice.getStatus());
        resp.setRemark(invoice.getRemark());
        return resp;
    }

    /**
     * 订单 → 列表项。
     *
     * @param order        订单
     * @param customerName 客户名称
     * @param salesmanName 业务员姓名
     * @return 列表项
     */
    public OrderResp toOrderResp(SalesOrder order, String customerName, String salesmanName) {
        OrderResp resp = new OrderResp();
        resp.setId(str(order.getId()));
        resp.setOrderNo(order.getOrderNo());
        resp.setCustomerId(str(order.getCustomerId()));
        resp.setCustomerName(customerName);
        resp.setCustomerOrderNo(order.getCustomerOrderNo());
        resp.setSalesmanId(str(order.getSalesmanId()));
        resp.setSalesmanName(salesmanName);
        resp.setOrderDate(order.getOrderDate());
        resp.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        resp.setStatus(order.getStatus() == null ? null : order.getStatus().getCode());
        resp.setStatusLabel(order.getStatus() == null ? null : order.getStatus().getLabel());
        resp.setCurrency(order.getCurrency());
        resp.setAmountTaxIncluded(order.totalAmountTaxIncluded());
        resp.setTotalQtyOrdered(order.totalQtyOrdered());
        resp.setTotalQtyShipped(order.totalQtyShipped());
        resp.setTotalQtyOpen(order.totalQtyOrdered().subtract(order.totalQtyShipped()));
        resp.setProgress(order.totalQtyOrdered().stripTrailingZeros().toPlainString()
                .equals("0") ? "0/0"
                : order.totalQtyShipped().stripTrailingZeros().toPlainString()
                + " / " + order.totalQtyOrdered().stripTrailingZeros().toPlainString());
        resp.setRemark(order.getRemark());
        resp.setCreateTime(order.getCreateTime());
        return resp;
    }

    /**
     * 订单 → 简要信息。
     *
     * @param order 订单
     * @return 简要信息
     */
    public OrderBriefResp toOrderBrief(SalesOrder order) {
        OrderBriefResp resp = new OrderBriefResp();
        resp.setId(str(order.getId()));
        resp.setOrderNo(order.getOrderNo());
        resp.setCustomerOrderNo(order.getCustomerOrderNo());
        resp.setOrderDate(order.getOrderDate());
        resp.setStatus(order.getStatus() == null ? null : order.getStatus().getCode());
        resp.setStatusLabel(order.getStatus() == null ? null : order.getStatus().getLabel());
        resp.setAmountTaxIncluded(order.totalAmountTaxIncluded());
        resp.setTotalQtyOrdered(order.totalQtyOrdered());
        resp.setTotalQtyShipped(order.totalQtyShipped());
        return resp;
    }

    /**
     * 订单 → 详情。
     *
     * @param order        订单
     * @param customerName 客户名称
     * @param salesmanName 业务员姓名
     * @return 详情
     */
    public OrderDetailResp toOrderDetail(SalesOrder order, String customerName, String salesmanName) {
        OrderDetailResp resp = new OrderDetailResp();
        resp.setId(str(order.getId()));
        resp.setOrderNo(order.getOrderNo());
        resp.setCustomerId(str(order.getCustomerId()));
        resp.setCustomerName(customerName);
        resp.setCustomerOrderNo(order.getCustomerOrderNo());
        resp.setRequirement(order.getRequirement());
        resp.setMainboardLabel(order.getMainboardLabel());
        resp.setCartonLabel(order.getCartonLabel());
        resp.setSalesmanId(str(order.getSalesmanId()));
        resp.setSalesmanName(salesmanName);
        resp.setOrderDate(order.getOrderDate());
        resp.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        resp.setStatus(order.getStatus() == null ? null : order.getStatus().getCode());
        resp.setStatusLabel(order.getStatus() == null ? null : order.getStatus().getLabel());
        resp.setCurrency(order.getCurrency());
        resp.setSettlementMethod(order.getSettlementMethod());
        resp.setDeliveryMethod(order.getDeliveryMethod());
        resp.setRemark(order.getRemark());
        resp.setItems(order.getItems().stream().map(this::toItemResp).toList());
        resp.setAmountTaxIncluded(order.totalAmountTaxIncluded());
        resp.setTotalQtyOrdered(order.totalQtyOrdered());
        resp.setTotalQtyShipped(order.totalQtyShipped());
        return resp;
    }

    /**
     * 明细 → 响应（含"含税/不含税"标签，供列头动态显示）。
     *
     * @param item 明细
     * @return 响应
     */
    public OrderItemResp toItemResp(SalesOrderItem item) {
        OrderItemResp resp = new OrderItemResp();
        resp.setId(str(item.getId()));
        resp.setOrderId(str(item.getOrderId()));
        resp.setLineNo(item.getLineNo());
        resp.setModel(item.getModel());
        resp.setMaterialNo(item.getMaterialNo());
        resp.setCustomerMaterialNo(item.getCustomerMaterialNo());
        resp.setSpec(item.getSpec());
        resp.setQtyOrdered(item.getQtyOrdered());
        resp.setQtyShipped(item.getQtyShipped());
        resp.setQtyOpen(item.getQtyOpen());
        resp.setTaxRate(item.getTaxRate());
        resp.setTaxRateLabel(rateLabel(item.getTaxRate()));
        resp.setUnitPrice(item.getUnitPrice());
        resp.setPriceIsTaxIncluded(item.getPriceIsTaxIncluded());
        resp.setAmountExTax(item.getAmountExTax());
        resp.setTaxAmount(item.getTaxAmount());
        resp.setAmountTaxIncluded(item.getAmountTaxIncluded());
        resp.setReconcileDate(item.getReconcileDate());
        resp.setReconcileStatus(item.getReconcileStatus() == null ? null : item.getReconcileStatus().getCode());
        resp.setReconcileStatusLabel(item.getReconcileStatus() == null ? null : item.getReconcileStatus().getLabel());
        resp.setStatus(item.getStatus() == null ? null : item.getStatus().getCode());
        resp.setStatusLabel(item.getStatus() == null ? null : item.getStatus().getLabel());
        resp.setRemark(item.getRemark());
        return resp;
    }

    /**
     * 出货 → 响应。
     *
     * @param shipment     出货
     * @param customerName 客户名称
     * @param salesmanName 业务员姓名
     * @param orderNo      关联订单号
     * @return 响应
     */
    public ShipmentResp toShipmentResp(Shipment shipment, String customerName, String salesmanName, String orderNo) {
        ShipmentResp resp = new ShipmentResp();
        resp.setId(str(shipment.getId()));
        resp.setShipDate(shipment.getShipDate());
        resp.setProcessOrderNo(shipment.getProcessOrderNo());
        resp.setSalesmanId(str(shipment.getSalesmanId()));
        resp.setSalesmanName(salesmanName);
        resp.setCustomerId(str(shipment.getCustomerId()));
        resp.setCustomerName(customerName);
        resp.setCustomerOrderNo(shipment.getCustomerOrderNo());
        resp.setCustomerMaterialNo(shipment.getCustomerMaterialNo());
        resp.setModel(shipment.getModel());
        resp.setMaterialNo(shipment.getMaterialNo());
        resp.setSpec(shipment.getSpec());
        resp.setQty(shipment.getQty());
        resp.setOrderItemId(str(shipment.getOrderItemId()));
        resp.setOrderNo(orderNo);
        resp.setAmount(shipment.getAmount());
        resp.setTaxAmount(shipment.getTaxAmount());
        resp.setAmountTaxIncluded(shipment.getAmountTaxIncluded());
        resp.setStatus(shipment.getStatus() == null ? null : shipment.getStatus().getCode());
        resp.setStatusLabel(shipment.getStatus() == null ? null : shipment.getStatus().getLabel());
        resp.setSource(shipment.getSource() == null ? null : shipment.getSource().getCode());
        resp.setSourceLabel(shipment.getSource() == null ? null : shipment.getSource().getLabel());
        resp.setImportBatchId(str(shipment.getImportBatchId()));
        resp.setRemark(shipment.getRemark());
        resp.setCreateTime(shipment.getCreateTime());
        return resp;
    }

    /**
     * 税率 → 百分比标签（13% / 不含税）。
     *
     * @param rate 税率
     * @return 标签
     */
    public String rateLabel(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return "不含税";
        }
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    /**
     * 订单状态标签（null 安全）。
     *
     * @param status 状态
     * @return 标签
     */
    public String orderStatusLabel(OrderStatus status) {
        return status == null ? "" : status.getLabel();
    }

    /**
     * 明细状态标签（null 安全）。
     *
     * @param status 状态
     * @return 标签
     */
    public String itemStatusLabel(ItemStatus status) {
        return status == null ? "" : status.getLabel();
    }

    /**
     * 对账状态标签（null 安全）。
     *
     * @param status 状态
     * @return 标签
     */
    public String reconcileStatusLabel(ReconcileStatus status) {
        return status == null ? "" : status.getLabel();
    }

    private String invoiceTypeLabel(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "SPECIAL" -> "增值税专用发票";
            case "NORMAL" -> "增值税普通发票";
            default -> type;
        };
    }

    private String str(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
