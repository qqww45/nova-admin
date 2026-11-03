package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdCustomerMapping;

import java.util.List;

/**
 * 金蝶客户 ↔ 内部客户映射仓储。
 *
 * @author admin-system
 * @since 2026-11-03
 */
public interface KdCustomerMappingRepository {

    /**
     * 查某归属人的全部映射。
     *
     * @param ownerUserId 数据归属人；null 表示不限（管理员）
     * @return 映射列表
     */
    List<KdCustomerMapping> listByOwner(Long ownerUserId);

    /**
     * 按归属人 + 账套 + 金蝶内码查一条。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param kdFid 金蝶客户内码
     * @return 映射；不存在返回 null
     */
    KdCustomerMapping find(Long ownerUserId, String acctId, Long kdFid);

    /**
     * 新增，返回主键。
     *
     * @param mapping 映射
     * @return 主键
     */
    Long save(KdCustomerMapping mapping);

    /**
     * 更新。
     *
     * @param mapping 映射
     */
    void update(KdCustomerMapping mapping);

    /**
     * 批量新增（自动建议一次生成多行）。
     *
     * @param mappings 映射列表
     */
    void saveAll(List<KdCustomerMapping> mappings);
}
