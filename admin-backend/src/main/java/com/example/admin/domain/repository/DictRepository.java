package com.example.admin.domain.repository;

import com.example.admin.domain.model.dict.DictItem;

import java.util.List;

/**
 * 业务字典仓储接口（税率等字典禁止硬编码，统一从库里读）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface DictRepository {

    /**
     * 按字典编码查询启用项。
     *
     * @param dictCode 字典编码
     * @return 字典项列表（按 sort 排序）
     */
    List<DictItem> listByCode(String dictCode);

    /**
     * 查询全部启用的字典项。
     *
     * @return 字典项列表
     */
    List<DictItem> listAll();
}
