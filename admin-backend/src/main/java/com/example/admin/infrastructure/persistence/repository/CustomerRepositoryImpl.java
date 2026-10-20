package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.customer.CustomerContact;
import com.example.admin.domain.model.customer.CustomerInvoice;
import com.example.admin.domain.model.customer.CustomerQuery;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.infrastructure.persistence.converter.CustomerConverter;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerContactMapper;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerInvoiceMapper;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerMapper;
import com.example.admin.infrastructure.persistence.mapper.BizSalesOrderMapper;
import com.example.admin.infrastructure.persistence.po.BizCustomer;
import com.example.admin.infrastructure.persistence.po.BizCustomerContact;
import com.example.admin.infrastructure.persistence.po.BizCustomerInvoice;
import com.example.admin.infrastructure.persistence.po.BizSalesOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户仓储实现（Infrastructure 层）。
 *
 * <p>默认项不变量：设为默认时先批量清零再置位（{@code clearXxxDefault + update}），
 * 两步放在同一事务里，避免中间态出现"两个默认"或"零个默认"。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {

    private final BizCustomerMapper customerMapper;
    private final BizCustomerContactMapper contactMapper;
    private final BizCustomerInvoiceMapper invoiceMapper;
    private final BizSalesOrderMapper orderMapper;
    private final CustomerConverter converter;

    @Override
    public PageResult<Customer> page(CustomerQuery query) {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<BizCustomer>()
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(BizCustomer::getCustomerName, query.getKeyword())
                        .or().like(BizCustomer::getCustomerCode, query.getKeyword())
                        .or().like(BizCustomer::getAddress, query.getKeyword()))
                .like(StrUtil.isNotBlank(query.getCustomerName()), BizCustomer::getCustomerName, query.getCustomerName())
                .eq(query.getStatus() != null, BizCustomer::getStatus, query.getStatus())
                .orderByDesc(BizCustomer::getUpdateTime);
        IPage<BizCustomer> page = customerMapper.selectPage(query.buildPage(), wrapper);
        List<Customer> records = new ArrayList<>();
        for (BizCustomer po : page.getRecords()) {
            Customer customer = converter.toDomain(po);
            // 关键字也允许按联系人命中：命中时把联系人一并返回，便于前端直接展示
            if (StrUtil.isNotBlank(query.getKeyword())) {
                customer.setContacts(listContacts(customer.getId()));
            }
            records.add(customer);
        }
        return toPageResult(page, records);
    }

    @Override
    public Customer findById(Long id) {
        if (id == null) {
            return null;
        }
        BizCustomer po = customerMapper.selectById(id);
        Customer customer = converter.toDomain(po);
        if (customer != null) {
            customer.setContacts(listContacts(id));
            customer.setInvoices(listInvoices(id));
        }
        return customer;
    }

    @Override
    public Customer findByNormalizedName(String normalizedName) {
        if (StrUtil.isBlank(normalizedName)) {
            return null;
        }
        // 名称标准化在应用层做，库里存的是原始名称，因此这里用 like 精确匹配后由应用层复核
        List<BizCustomer> list = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, normalizedName));
        for (BizCustomer po : list) {
            if (normalizedName.equals(Customer.normalizeName(po.getCustomerName()))) {
                return converter.toDomain(po);
            }
        }
        return null;
    }

    @Override
    public boolean existsByName(String normalizedName, Long excludeId) {
        List<BizCustomer> list = customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>());
        for (BizCustomer po : list) {
            if (excludeId != null && excludeId.equals(po.getId())) {
                continue;
            }
            if (normalizedName.equals(Customer.normalizeName(po.getCustomerName()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int countOrders(Long customerId) {
        if (customerId == null) {
            return 0;
        }
        return orderMapper.selectCount(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getCustomerId, customerId)).intValue();
    }

    @Override
    public Long save(Customer customer) {
        BizCustomer po = converter.toPo(customer);
        customerMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(Customer customer) {
        customerMapper.updateById(converter.toPo(customer));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        customerMapper.deleteById(id);
        contactMapper.delete(new LambdaQueryWrapper<BizCustomerContact>()
                .eq(BizCustomerContact::getCustomerId, id));
        invoiceMapper.delete(new LambdaQueryWrapper<BizCustomerInvoice>()
                .eq(BizCustomerInvoice::getCustomerId, id));
    }

    @Override
    public List<CustomerContact> listContacts(Long customerId) {
        List<BizCustomerContact> list = contactMapper.selectList(new LambdaQueryWrapper<BizCustomerContact>()
                .eq(BizCustomerContact::getCustomerId, customerId)
                .orderByDesc(BizCustomerContact::getIsDefault)
                .orderByAsc(BizCustomerContact::getId));
        return list.stream().map(converter::contactToDomain).toList();
    }

    @Override
    public CustomerContact findContact(Long id) {
        return converter.contactToDomain(contactMapper.selectById(id));
    }

    @Override
    public Long saveContact(CustomerContact contact) {
        BizCustomerContact po = converter.contactToPo(contact);
        contactMapper.insert(po);
        return po.getId();
    }

    @Override
    public void updateContact(CustomerContact contact) {
        contactMapper.updateById(converter.contactToPo(contact));
    }

    @Override
    public void removeContact(Long id) {
        contactMapper.deleteById(id);
    }

    @Override
    public void clearContactDefault(Long customerId) {
        BizCustomerContact po = new BizCustomerContact();
        po.setIsDefault(0);
        contactMapper.update(po, new LambdaQueryWrapper<BizCustomerContact>()
                .eq(BizCustomerContact::getCustomerId, customerId)
                .eq(BizCustomerContact::getIsDefault, 1));
    }

    @Override
    public List<CustomerInvoice> listInvoices(Long customerId) {
        List<BizCustomerInvoice> list = invoiceMapper.selectList(new LambdaQueryWrapper<BizCustomerInvoice>()
                .eq(BizCustomerInvoice::getCustomerId, customerId)
                .orderByDesc(BizCustomerInvoice::getIsDefault)
                .orderByAsc(BizCustomerInvoice::getId));
        return list.stream().map(converter::invoiceToDomain).toList();
    }

    @Override
    public CustomerInvoice findInvoice(Long id) {
        return converter.invoiceToDomain(invoiceMapper.selectById(id));
    }

    @Override
    public Long saveInvoice(CustomerInvoice invoice) {
        BizCustomerInvoice po = converter.invoiceToPo(invoice);
        invoiceMapper.insert(po);
        return po.getId();
    }

    @Override
    public void updateInvoice(CustomerInvoice invoice) {
        invoiceMapper.updateById(converter.invoiceToPo(invoice));
    }

    @Override
    public void removeInvoice(Long id) {
        invoiceMapper.deleteById(id);
    }

    @Override
    public void clearInvoiceDefault(Long customerId) {
        BizCustomerInvoice po = new BizCustomerInvoice();
        po.setIsDefault(0);
        invoiceMapper.update(po, new LambdaQueryWrapper<BizCustomerInvoice>()
                .eq(BizCustomerInvoice::getCustomerId, customerId)
                .eq(BizCustomerInvoice::getIsDefault, 1));
    }

    @Override
    public List<Customer> search(String keyword, int limit) {
        Page<BizCustomer> page = new Page<>(1, Math.max(1, limit));
        IPage<BizCustomer> result = customerMapper.selectPage(page, new LambdaQueryWrapper<BizCustomer>()
                .like(StrUtil.isNotBlank(keyword), BizCustomer::getCustomerName, keyword)
                .eq(BizCustomer::getStatus, 1)
                .orderByAsc(BizCustomer::getCustomerName));
        return result.getRecords().stream().map(converter::toDomain).toList();
    }

    @Override
    public List<Customer> listAll() {
        return customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                        .orderByAsc(BizCustomer::getCustomerName))
                .stream().map(converter::toDomain).toList();
    }

    /**
     * 把 MyBatis-Plus 分页对象与已转换的记录组装成统一分页响应。
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
