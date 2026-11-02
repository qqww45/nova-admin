package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.kd.KdFieldMapping;
import com.example.admin.domain.repository.KdFieldMappingRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdFieldMappingMapper;
import com.example.admin.infrastructure.persistence.po.KdFieldMappingPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 账套字段映射仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdFieldMappingRepositoryImpl implements KdFieldMappingRepository {

    private final KdFieldMappingMapper mappingMapper;
    private final KdConverter converter;

    @Override
    public List<KdFieldMapping> listByBinding(Long bindingId) {
        return mappingMapper.selectList(new LambdaQueryWrapper<KdFieldMappingPo>()
                        .eq(KdFieldMappingPo::getBindingId, bindingId)
                        .orderByAsc(KdFieldMappingPo::getMappingKey))
                .stream().map(converter::mappingToDomain).toList();
    }

    @Override
    public KdFieldMapping find(Long bindingId, String key) {
        KdFieldMappingPo po = mappingMapper.selectOne(new LambdaQueryWrapper<KdFieldMappingPo>()
                .eq(KdFieldMappingPo::getBindingId, bindingId)
                .eq(KdFieldMappingPo::getMappingKey, key));
        return converter.mappingToDomain(po);
    }

    @Override
    public Long save(KdFieldMapping mapping) {
        KdFieldMappingPo po = converter.mappingToPo(mapping);
        mappingMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(KdFieldMapping mapping) {
        mappingMapper.updateById(converter.mappingToPo(mapping));
    }
}
