package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdBindingMapper;
import com.example.admin.infrastructure.persistence.po.KdBindingPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集器绑定仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class KdBindingRepositoryImpl implements KdBindingRepository {

    private final KdBindingMapper bindingMapper;
    private final KdConverter converter;

    @Override
    public KdBinding findById(Long id) {
        return id == null ? null : converter.bindingToDomain(bindingMapper.selectById(id));
    }

    @Override
    public KdBinding findByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return null;
        }
        KdBindingPo po = bindingMapper.selectOne(new LambdaQueryWrapper<KdBindingPo>()
                .eq(KdBindingPo::getTokenHash, tokenHash));
        return converter.bindingToDomain(po);
    }

    @Override
    public KdBinding findByUserAndAcct(Long userId, String acctId) {
        if (userId == null || acctId == null || acctId.isBlank()) {
            return null;
        }
        KdBindingPo po = bindingMapper.selectOne(new LambdaQueryWrapper<KdBindingPo>()
                .eq(KdBindingPo::getUserId, userId)
                .eq(KdBindingPo::getAcctId, acctId));
        return converter.bindingToDomain(po);
    }

    @Override
    public List<KdBinding> listByUser(Long userId) {
        LambdaQueryWrapper<KdBindingPo> wrapper = new LambdaQueryWrapper<KdBindingPo>()
                .eq(userId != null, KdBindingPo::getUserId, userId)
                .orderByDesc(KdBindingPo::getUpdateTime);
        return bindingMapper.selectList(wrapper).stream().map(converter::bindingToDomain).toList();
    }

    @Override
    public Long save(KdBinding binding) {
        KdBindingPo po = converter.bindingToPo(binding);
        bindingMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(KdBinding binding) {
        bindingMapper.updateById(converter.bindingToPo(binding));
    }

    @Override
    public void touch(Long id, LocalDateTime now) {
        // 同一分钟内只写一次：鉴权是每个请求都要走的路径，无谓的 UPDATE 会放大写压力
        int rows = bindingMapper.update(null, new LambdaUpdateWrapper<KdBindingPo>()
                .eq(KdBindingPo::getId, id)
                .and(w -> w.isNull(KdBindingPo::getLastUsedAt)
                        .or().lt(KdBindingPo::getLastUsedAt, now.minusMinutes(1)))
                .set(KdBindingPo::getLastUsedAt, now));
        if (rows == 0) {
            log.debug("绑定 {} 使用时间已是最新，跳过写入", id);
        }
    }
}
