package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdFieldMapping;

import java.util.List;

/**
 * 账套字段映射仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdFieldMappingRepository {

    /**
     * 查询某绑定的全部映射。
     *
     * @param bindingId 绑定 ID
     * @return 映射列表
     */
    List<KdFieldMapping> listByBinding(Long bindingId);

    /**
     * 按绑定 + 键查询。
     *
     * @param bindingId 绑定 ID
     * @param key 映射键
     * @return 映射；不存在返回 null
     */
    KdFieldMapping find(Long bindingId, String key);

    /**
     * 新增映射。
     *
     * @param mapping 映射
     * @return 新主键
     */
    Long save(KdFieldMapping mapping);

    /**
     * 更新映射。
     *
     * @param mapping 映射
     */
    void update(KdFieldMapping mapping);
}
