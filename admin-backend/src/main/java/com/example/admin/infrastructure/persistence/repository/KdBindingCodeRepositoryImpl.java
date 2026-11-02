package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.admin.domain.model.kd.KdBindingCode;
import com.example.admin.domain.repository.KdBindingCodeRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdBindingCodeMapper;
import com.example.admin.infrastructure.persistence.po.KdBindingCodePo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 一次性绑定码仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdBindingCodeRepositoryImpl implements KdBindingCodeRepository {

    private final KdBindingCodeMapper codeMapper;
    private final KdConverter converter;

    @Override
    public KdBindingCode findByHash(String codeHash) {
        if (codeHash == null || codeHash.isBlank()) {
            return null;
        }
        KdBindingCodePo po = codeMapper.selectOne(new LambdaQueryWrapper<KdBindingCodePo>()
                .eq(KdBindingCodePo::getCodeHash, codeHash));
        return converter.codeToDomain(po);
    }

    @Override
    public Long save(KdBindingCode code) {
        KdBindingCodePo po = converter.codeToPo(code);
        codeMapper.insert(po);
        return po.getId();
    }

    @Override
    public boolean markUsed(Long id, LocalDateTime usedAt) {
        // 以 used_at 为空作为抢占条件：并发兑换时只有一个人能成功，天然防重放
        int rows = codeMapper.update(null, new LambdaUpdateWrapper<KdBindingCodePo>()
                .eq(KdBindingCodePo::getId, id)
                .isNull(KdBindingCodePo::getUsedAt)
                .set(KdBindingCodePo::getUsedAt, usedAt));
        return rows > 0;
    }
}
