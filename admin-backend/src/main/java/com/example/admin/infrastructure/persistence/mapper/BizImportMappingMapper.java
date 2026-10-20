package com.example.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.infrastructure.persistence.po.BizImportMapping;

/**
 * BizImportMapping 数据访问接口。
 *
 * <p>全部查询走 BaseMapper + LambdaQueryWrapper（本工程不使用 mapper XML），
 * 由 @MapperScan 统一注册，无需 @Mapper 注解。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface BizImportMappingMapper extends BaseMapper<BizImportMapping> {
}
