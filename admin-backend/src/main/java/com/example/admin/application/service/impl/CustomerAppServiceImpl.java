package com.example.admin.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.assembler.BizAssembler;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.customer.CustomerContact;
import com.example.admin.domain.model.customer.CustomerInvoice;
import com.example.admin.domain.model.customer.CustomerQuery;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.OrderRepository;
import com.example.admin.application.service.CustomerAppService;
import com.example.admin.interfaces.dto.req.CustomerContactReq;
import com.example.admin.interfaces.dto.req.CustomerInvoiceReq;
import com.example.admin.interfaces.dto.req.CustomerPageQuery;
import com.example.admin.interfaces.dto.req.CustomerSaveReq;
import com.example.admin.interfaces.dto.resp.CustomerDetailResp;
import com.example.admin.interfaces.dto.resp.CustomerResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户应用服务实现。
 *
 * <p>业务规则集中在这里（需求 6.1）：
 * <ul>
 *     <li>客户名称标准化后唯一，停用不算冲突；</li>
 *     <li>删除前校验是否有订单，有则禁止删除只允许停用；</li>
 *     <li>联系人 / 开票信息：设为默认时先清零再置位；删除默认项后若还有其它启用项，强制重新指定；
 *         最后一条删除时允许无默认并给出提示。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerAppServiceImpl implements CustomerAppService {

    /** 客户详情里最多展示的关联订单数 */
    private static final int ORDER_LIMIT = 50;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final BizAssembler assembler;

    @Override
    public PageResult<CustomerResp> page(CustomerPageQuery query) {
        CustomerQuery domainQuery = new CustomerQuery();
        domainQuery.setPageNum(query.getPageNum());
        domainQuery.setPageSize(query.getPageSize());
        domainQuery.setSortBy(query.getSortBy());
        domainQuery.setSortOrder(query.getSortOrder());
        domainQuery.setKeyword(query.getKeyword());
        domainQuery.setCustomerName(query.getCustomerName());
        domainQuery.setStatus(query.getStatus());

        PageResult<Customer> page = customerRepository.page(domainQuery);
        List<CustomerResp> records = new ArrayList<>();
        for (Customer customer : page.getRecords()) {
            CustomerResp resp = assembler.toCustomerResp(customer);
            resp.setOrderCount(customerRepository.countOrders(customer.getId()));
            records.add(resp);
        }
        return toPage(page, records);
    }

    @Override
    public CustomerDetailResp detail(String id) {
        Customer customer = customerRepository.findById(IdParser.parseId(id));
        if (customer == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在或已被删除");
        }
        List<SalesOrder> orders = orderRepository.listByCustomer(customer.getId(), ORDER_LIMIT);
        return assembler.toCustomerDetail(customer, orders, customerRepository.countOrders(customer.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(CustomerSaveReq req) {
        String normalized = Customer.normalizeName(req.getCustomerName());
        if (customerRepository.existsByName(normalized, null)) {
            throw new BusinessException(ResultCode.DATA_EXIST, "客户名称已存在：" + req.getCustomerName());
        }
        Customer customer = new Customer();
        customer.setCustomerName(req.getCustomerName().trim());
        customer.setCustomerCode(req.getCustomerCode());
        customer.setAddress(req.getAddress());
        customer.setRemark(req.getRemark());
        customer.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        Long id = customerRepository.save(customer);
        return String.valueOf(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerSaveReq req) {
        Long id = IdParser.parseId(req.getId());
        Customer existing = customerRepository.findById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在或已被删除");
        }
        String normalized = Customer.normalizeName(req.getCustomerName());
        if (customerRepository.existsByName(normalized, id)) {
            throw new BusinessException(ResultCode.DATA_EXIST, "客户名称已存在：" + req.getCustomerName());
        }
        existing.setCustomerName(req.getCustomerName().trim());
        existing.setCustomerCode(req.getCustomerCode());
        existing.setAddress(req.getAddress());
        existing.setRemark(req.getRemark());
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        customerRepository.update(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<String> ids) {
        for (String id : ids) {
            Long customerId = IdParser.parseId(id);
            int orderCount = customerRepository.countOrders(customerId);
            if (orderCount > 0) {
                Customer customer = customerRepository.findById(customerId);
                throw new BusinessException("客户【"
                        + (customer == null ? customerId : customer.getCustomerName())
                        + "】已存在 " + orderCount + " 笔订单，不允许删除，请改为停用");
            }
            customerRepository.remove(customerId);
        }
    }

    @Override
    public List<CustomerResp> search(String keyword, int limit) {
        return customerRepository.search(keyword, limit).stream().map(assembler::toCustomerResp).toList();
    }

    /* ---------------- 联系人 ---------------- */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveContact(CustomerContactReq req) {
        Long customerId = IdParser.parseId(req.getCustomerId());
        requireCustomer(customerId);
        if (StrUtil.isBlank(req.getPhone()) && StrUtil.isBlank(req.getMobile())) {
            throw new BusinessException("电话与手机至少填写一个");
        }
        CustomerContact contact = new CustomerContact();
        if (StrUtil.isNotBlank(req.getId())) {
            contact.setId(IdParser.parseId(req.getId()));
        }
        contact.setCustomerId(customerId);
        contact.setContactName(req.getContactName());
        contact.setTitle(req.getTitle());
        contact.setPhone(req.getPhone());
        contact.setMobile(req.getMobile());
        contact.setEmail(req.getEmail());
        contact.setIsDefault(req.getIsDefault() == null ? 0 : req.getIsDefault());
        contact.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        contact.setRemark(req.getRemark());

        if (contact.getId() == null) {
            Long id = customerRepository.saveContact(contact);
            contact.setId(id);
        } else {
            customerRepository.updateContact(contact);
        }
        // 设为默认：先清零再置位，保证"有且仅有一个"
        if (contact.isDefaultContact()) {
            customerRepository.clearContactDefault(customerId);
            contact.setIsDefault(1);
            customerRepository.updateContact(contact);
        }
        ensureSingleDefaultContact(customerId);
        return String.valueOf(contact.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeContact(String id) {
        CustomerContact contact = customerRepository.findContact(IdParser.parseId(id));
        if (contact == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "联系人不存在");
        }
        customerRepository.removeContact(contact.getId());
        ensureSingleDefaultContact(contact.getCustomerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultContact(String id) {
        CustomerContact contact = customerRepository.findContact(IdParser.parseId(id));
        if (contact == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "联系人不存在");
        }
        customerRepository.clearContactDefault(contact.getCustomerId());
        contact.setIsDefault(1);
        contact.setStatus(1);
        customerRepository.updateContact(contact);
    }

    /* ---------------- 开票信息 ---------------- */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveInvoice(CustomerInvoiceReq req) {
        Long customerId = IdParser.parseId(req.getCustomerId());
        requireCustomer(customerId);
        CustomerInvoice invoice = new CustomerInvoice();
        if (StrUtil.isNotBlank(req.getId())) {
            invoice.setId(IdParser.parseId(req.getId()));
        }
        invoice.setCustomerId(customerId);
        invoice.setInvoiceTitle(req.getInvoiceTitle());
        invoice.setTaxNo(req.getTaxNo());
        invoice.setBankName(req.getBankName());
        invoice.setBankAccount(req.getBankAccount());
        invoice.setInvoiceAddress(req.getInvoiceAddress());
        invoice.setInvoicePhone(req.getInvoicePhone());
        invoice.setInvoiceType(req.getInvoiceType());
        invoice.setIsDefault(req.getIsDefault() == null ? 0 : req.getIsDefault());
        invoice.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        invoice.setRemark(req.getRemark());

        if (invoice.getId() == null) {
            Long id = customerRepository.saveInvoice(invoice);
            invoice.setId(id);
        } else {
            customerRepository.updateInvoice(invoice);
        }
        if (invoice.isDefaultInvoice()) {
            customerRepository.clearInvoiceDefault(customerId);
            invoice.setIsDefault(1);
            customerRepository.updateInvoice(invoice);
        }
        ensureSingleDefaultInvoice(customerId);
        return String.valueOf(invoice.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeInvoice(String id) {
        CustomerInvoice invoice = customerRepository.findInvoice(IdParser.parseId(id));
        if (invoice == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "开票信息不存在");
        }
        customerRepository.removeInvoice(invoice.getId());
        ensureSingleDefaultInvoice(invoice.getCustomerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultInvoice(String id) {
        CustomerInvoice invoice = customerRepository.findInvoice(IdParser.parseId(id));
        if (invoice == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "开票信息不存在");
        }
        customerRepository.clearInvoiceDefault(invoice.getCustomerId());
        invoice.setIsDefault(1);
        invoice.setStatus(1);
        customerRepository.updateInvoice(invoice);
    }

    /* ---------------- 私有辅助 ---------------- */

    private void requireCustomer(Long customerId) {
        if (customerRepository.findById(customerId) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在或已被删除");
        }
    }

    /**
     * 保证默认联系人的唯一性：删掉默认项后若仍有启用项，自动把第一条置为默认；
     * 一条不剩时允许无默认（提示由前端给出）。
     *
     * @param customerId 客户 ID
     */
    private void ensureSingleDefaultContact(Long customerId) {
        List<CustomerContact> contacts = customerRepository.listContacts(customerId).stream()
                .filter(CustomerContact::isEnabled).toList();
        if (contacts.isEmpty()) {
            return;
        }
        long defaultCount = contacts.stream().filter(CustomerContact::isDefaultContact).count();
        if (defaultCount == 1) {
            return;
        }
        customerRepository.clearContactDefault(customerId);
        CustomerContact first = contacts.get(0);
        first.setIsDefault(1);
        customerRepository.updateContact(first);
    }

    /** 默认开票信息的唯一性，规则同联系人 */
    private void ensureSingleDefaultInvoice(Long customerId) {
        List<CustomerInvoice> invoices = customerRepository.listInvoices(customerId).stream()
                .filter(CustomerInvoice::isEnabled).toList();
        if (invoices.isEmpty()) {
            return;
        }
        long defaultCount = invoices.stream().filter(CustomerInvoice::isDefaultInvoice).count();
        if (defaultCount == 1) {
            return;
        }
        customerRepository.clearInvoiceDefault(customerId);
        CustomerInvoice first = invoices.get(0);
        first.setIsDefault(1);
        customerRepository.updateInvoice(first);
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
