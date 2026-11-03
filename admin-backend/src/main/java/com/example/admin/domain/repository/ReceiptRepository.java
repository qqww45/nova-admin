package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.receipt.Receipt;
import com.example.admin.domain.model.receipt.ReceiptQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款登记仓储接口（领域层）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
public interface ReceiptRepository {

    /** 分页查询 */
    PageResult<Receipt> page(ReceiptQuery query);

    /** 按 ID 查询 */
    Receipt findById(Long id);

    /** 新增，返回主键 */
    Long save(Receipt receipt);

    /** 更新 */
    void update(Receipt receipt);

    /** 按收款单号查询（幂等/生成对账单时定位用） */
    Receipt findByReceiptNo(String receiptNo);

    /** 查询某客户在指定期间内的收款（对账单聚合用，已按日期升序，自动排除已作废） */
    List<Receipt> listByCustomerAndPeriod(Long customerId, LocalDateTime start, LocalDateTime end);

    /**
     * 全部有效收款（排除作废），应收速览等内存聚合用。
     *
     * @return 收款列表，按收款日期升序
     */
    List<Receipt> listAll();
}
