package com.example.admin.domain.repository;

import com.example.admin.domain.model.mapping.ImportMapping;

import java.util.List;

/**
 * 导入字段映射仓储接口（配置化，禁止把列头别名写死在代码里）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ImportMappingRepository {

    /**
     * 按模块查询字段映射。
     *
     * @param module 模块：CUSTOMER / ORDER / SHIPMENT
     * @return 映射列表（按列序排序）
     */
    List<ImportMapping> listByModule(String module);
}
