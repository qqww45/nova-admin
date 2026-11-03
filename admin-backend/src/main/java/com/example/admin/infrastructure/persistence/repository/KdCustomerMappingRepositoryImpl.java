package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.kd.KdCustomerMapping;
import com.example.admin.domain.repository.KdCustomerMappingRepository;
import com.example.admin.infrastructure.persistence.mapper.KdCustomerMappingMapper;
import com.example.admin.infrastructure.persistence.po.KdCustomerMappingPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 金蝶客户 ↔ 内部客户映射仓储实现。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Repository
@RequiredArgsConstructor
public class KdCustomerMappingRepositoryImpl implements KdCustomerMappingRepository {

    private final KdCustomerMappingMapper mappingMapper;

    @Override
    public List<KdCustomerMapping> listByOwner(Long ownerUserId) {
        LambdaQueryWrapper<KdCustomerMappingPo> wrapper = new LambdaQueryWrapper<KdCustomerMappingPo>()
                .eq(ownerUserId != null, KdCustomerMappingPo::getOwnerUserId, ownerUserId)
                .orderByAsc(KdCustomerMappingPo::getAcctId)
                .orderByAsc(KdCustomerMappingPo::getKdFid);
        return mappingMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public KdCustomerMapping find(Long ownerUserId, String acctId, Long kdFid) {
        List<KdCustomerMappingPo> list = mappingMapper.selectList(
                new LambdaQueryWrapper<KdCustomerMappingPo>()
                        .eq(KdCustomerMappingPo::getOwnerUserId, ownerUserId)
                        .eq(KdCustomerMappingPo::getAcctId, acctId)
                        .eq(KdCustomerMappingPo::getKdFid, kdFid)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : toDomain(list.get(0));
    }

    @Override
    public Long save(KdCustomerMapping mapping) {
        KdCustomerMappingPo po = toPo(mapping);
        mappingMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(KdCustomerMapping mapping) {
        mappingMapper.updateById(toPo(mapping));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(List<KdCustomerMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        for (KdCustomerMapping mapping : mappings) {
            save(mapping);
        }
    }

    private KdCustomerMapping toDomain(KdCustomerMappingPo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdCustomerMapping.class);
    }

    private KdCustomerMappingPo toPo(KdCustomerMapping domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdCustomerMappingPo.class);
    }
}
