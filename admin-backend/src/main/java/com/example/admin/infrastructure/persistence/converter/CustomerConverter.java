package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.customer.CustomerContact;
import com.example.admin.domain.model.customer.CustomerInvoice;
import com.example.admin.infrastructure.persistence.po.BizCustomer;
import com.example.admin.infrastructure.persistence.po.BizCustomerContact;
import com.example.admin.infrastructure.persistence.po.BizCustomerInvoice;
import org.springframework.stereotype.Component;

/**
 * 客户聚合 PO ⇄ 领域模型转换器。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class CustomerConverter {

    /**
     * PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public Customer toDomain(BizCustomer po) {
        if (po == null) {
            return null;
        }
        Customer customer = new Customer();
        BeanUtil.copyProperties(po, customer);
        return customer;
    }

    /**
     * 领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizCustomer toPo(Customer domain) {
        if (domain == null) {
            return null;
        }
        BizCustomer po = new BizCustomer();
        BeanUtil.copyProperties(domain, po);
        return po;
    }

    /**
     * 联系人 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public CustomerContact contactToDomain(BizCustomerContact po) {
        if (po == null) {
            return null;
        }
        CustomerContact contact = new CustomerContact();
        BeanUtil.copyProperties(po, contact);
        return contact;
    }

    /**
     * 联系人领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizCustomerContact contactToPo(CustomerContact domain) {
        if (domain == null) {
            return null;
        }
        BizCustomerContact po = new BizCustomerContact();
        BeanUtil.copyProperties(domain, po);
        return po;
    }

    /**
     * 开票信息 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public CustomerInvoice invoiceToDomain(BizCustomerInvoice po) {
        if (po == null) {
            return null;
        }
        CustomerInvoice invoice = new CustomerInvoice();
        BeanUtil.copyProperties(po, invoice);
        return invoice;
    }

    /**
     * 开票信息领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizCustomerInvoice invoiceToPo(CustomerInvoice domain) {
        if (domain == null) {
            return null;
        }
        BizCustomerInvoice po = new BizCustomerInvoice();
        BeanUtil.copyProperties(domain, po);
        return po;
    }
}
