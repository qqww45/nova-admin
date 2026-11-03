package com.example.admin.domain.repository;

import com.example.admin.domain.model.reconciliation.CustomerOpening;

import java.util.List;

/**
 * 客户期初应收仓储接口（领域层）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
public interface CustomerOpeningRepository {

    /** 按客户 ID 查询（唯一） */
    CustomerOpening findByCustomerId(Long customerId);

    /**
     * 全部客户期初应收，应收速览等内存聚合用。
     *
     * @return 期初列表
     */
    List<CustomerOpening> listAll();

    /** 新增，返回主键 */
    Long save(CustomerOpening opening);

    /** 更新 */
    void update(CustomerOpening opening);
}
