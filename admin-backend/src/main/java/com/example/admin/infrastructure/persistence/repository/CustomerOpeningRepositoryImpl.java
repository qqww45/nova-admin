package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.reconciliation.CustomerOpening;
import com.example.admin.domain.repository.CustomerOpeningRepository;
import com.example.admin.infrastructure.persistence.converter.BizSupportConverter;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerOpeningMapper;
import com.example.admin.infrastructure.persistence.po.BizCustomerOpening;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 客户期初应收仓储实现。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class CustomerOpeningRepositoryImpl implements CustomerOpeningRepository {

    private final BizCustomerOpeningMapper openingMapper;
    private final BizSupportConverter converter;

    @Override
    public CustomerOpening findByCustomerId(Long customerId) {
        if (customerId == null) {
            return null;
        }
        List<BizCustomerOpening> list = openingMapper.selectList(
                new LambdaQueryWrapper<BizCustomerOpening>()
                        .eq(BizCustomerOpening::getCustomerId, customerId).last("LIMIT 1"));
        return list.isEmpty() ? null : converter.openingToDomain(list.get(0));
    }

    @Override
    public List<CustomerOpening> listAll() {
        return openingMapper.selectList(new LambdaQueryWrapper<BizCustomerOpening>())
                .stream().map(converter::openingToDomain).toList();
    }

    @Override
    public Long save(CustomerOpening opening) {
        BizCustomerOpening po = converter.openingToPo(opening);
        openingMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(CustomerOpening opening) {
        openingMapper.updateById(converter.openingToPo(opening));
    }
}
