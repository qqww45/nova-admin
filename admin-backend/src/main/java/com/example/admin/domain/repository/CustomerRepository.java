package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.customer.CustomerContact;
import com.example.admin.domain.model.customer.CustomerInvoice;
import com.example.admin.domain.model.customer.CustomerQuery;

import java.util.List;

/**
 * 客户仓储接口（领域层，不含任何 ORM 细节）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface CustomerRepository {

    /**
     * 分页查询客户。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Customer> page(CustomerQuery query);

    /**
     * 按 ID 查询客户（含联系人与开票信息）。
     *
     * @param id 客户 ID
     * @return 客户，不存在返回 null
     */
    Customer findById(Long id);

    /**
     * 按标准化后的名称查询客户。
     *
     * @param normalizedName 标准化名称
     * @return 客户，不存在返回 null
     */
    Customer findByNormalizedName(String normalizedName);

    /**
     * 名称是否已存在（唯一性校验）。
     *
     * @param normalizedName 标准化名称
     * @param excludeId      排除的客户 ID（修改时用），可为 null
     * @return true 表示已存在
     */
    boolean existsByName(String normalizedName, Long excludeId);

    /**
     * 统计客户关联订单数（删除前校验）。
     *
     * @param customerId 客户 ID
     * @return 订单数
     */
    int countOrders(Long customerId);

    /**
     * 新增客户。
     *
     * @param customer 客户
     * @return 新 ID
     */
    Long save(Customer customer);

    /**
     * 更新客户。
     *
     * @param customer 客户
     */
    void update(Customer customer);

    /**
     * 逻辑删除客户。
     *
     * @param id 客户 ID
     */
    void remove(Long id);

    /* ---------------- 联系人 ---------------- */

    /**
     * 查询客户下的联系人。
     *
     * @param customerId 客户 ID
     * @return 联系人列表
     */
    List<CustomerContact> listContacts(Long customerId);

    /**
     * 查询单个联系人。
     *
     * @param id 联系人 ID
     * @return 联系人，不存在返回 null
     */
    CustomerContact findContact(Long id);

    /**
     * 新增联系人。
     *
     * @param contact 联系人
     * @return 新 ID
     */
    Long saveContact(CustomerContact contact);

    /**
     * 更新联系人。
     *
     * @param contact 联系人
     */
    void updateContact(CustomerContact contact);

    /**
     * 逻辑删除联系人。
     *
     * @param id 联系人 ID
     */
    void removeContact(Long id);

    /**
     * 批量取消某客户下所有联系人的默认标记（设为默认前调用）。
     *
     * @param customerId 客户 ID
     */
    void clearContactDefault(Long customerId);

    /* ---------------- 开票信息 ---------------- */

    /**
     * 查询客户下的开票信息。
     *
     * @param customerId 客户 ID
     * @return 开票信息列表
     */
    List<CustomerInvoice> listInvoices(Long customerId);

    /**
     * 查询单个开票信息。
     *
     * @param id 开票信息 ID
     * @return 开票信息，不存在返回 null
     */
    CustomerInvoice findInvoice(Long id);

    /**
     * 新增开票信息。
     *
     * @param invoice 开票信息
     * @return 新 ID
     */
    Long saveInvoice(CustomerInvoice invoice);

    /**
     * 更新开票信息。
     *
     * @param invoice 开票信息
     */
    void updateInvoice(CustomerInvoice invoice);

    /**
     * 逻辑删除开票信息。
     *
     * @param id 开票信息 ID
     */
    void removeInvoice(Long id);

    /**
     * 批量取消某客户下所有开票信息的默认标记。
     *
     * @param customerId 客户 ID
     */
    void clearInvoiceDefault(Long customerId);

    /**
     * 模糊匹配客户（按名称前缀，供下拉与导入匹配使用）。
     *
     * @param keyword 关键字
     * @param limit   返回条数上限
     * @return 客户列表
     */
    List<Customer> search(String keyword, int limit);

    /**
     * 查询全部客户（体检报告用）。
     *
     * <p>只用于《历史数据体检报告》这类一次性全量扫描，
     * 日常分页查询请走 {@link #page}，不要拿这个方法顶替列表接口。
     *
     * @return 客户列表（按名称排序）
     */
    List<Customer> listAll();
}
