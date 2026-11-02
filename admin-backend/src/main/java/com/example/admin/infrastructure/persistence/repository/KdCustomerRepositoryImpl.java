package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdUpsertResult;
import com.example.admin.domain.repository.KdCustomerRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdCustomerMapper;
import com.example.admin.infrastructure.persistence.po.KdCustomerPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 金蝶客户仓储实现。
 *
 * <p>upsert 口径：先按 {@code (owner, acct, kdFid)} 批量查出已存在的行，
 * 再逐行 insert / updateById —— 不写原生 {@code ON CONFLICT}，
 * 保证 SQLite / MySQL / PostgreSQL 三套库零改动。单批建议 ≤ 2000 行。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdCustomerRepositoryImpl implements KdCustomerRepository {

    /** IN 查询分片大小：避免 SQLite 变量上限与超长 SQL */
    private static final int IN_CHUNK = 500;

    private final KdCustomerMapper customerMapper;
    private final KdConverter converter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KdUpsertResult upsertAll(Long ownerUserId, String acctId, String batchNo, List<KdCustomer> rows) {
        if (rows == null || rows.isEmpty()) {
            return KdUpsertResult.empty();
        }
        List<KdCustomer> valid = rows.stream()
                .filter(r -> r.getKdFid() != null && r.getKdFid() > 0)
                .toList();
        int skipped = rows.size() - valid.size();
        Map<Long, Long> existing = findExistingIds(ownerUserId, acctId,
                valid.stream().map(KdCustomer::getKdFid).distinct().toList());
        LocalDateTime now = LocalDateTime.now();
        int upserted = 0;
        for (KdCustomer row : valid) {
            KdCustomerPo po = converter.customerToPo(row);
            po.setOwnerUserId(ownerUserId);
            po.setAcctId(acctId);
            po.setBatchNo(batchNo);
            po.setSyncAt(now);
            Long id = existing.get(row.getKdFid());
            if (id != null) {
                po.setId(id);
                customerMapper.updateById(po);
            } else {
                customerMapper.insert(po);
                existing.put(row.getKdFid(), po.getId());
            }
            upserted++;
        }
        return new KdUpsertResult(rows.size(), upserted, skipped);
    }

    @Override
    public long countByOwner(Long ownerUserId, String acctId) {
        return customerMapper.selectCount(new LambdaQueryWrapper<KdCustomerPo>()
                .eq(KdCustomerPo::getOwnerUserId, ownerUserId)
                .eq(KdCustomerPo::getAcctId, acctId));
    }

    /**
     * 批量查已存在的主键。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param fids 金蝶客户内码
     * @return kdFid → 主键
     */
    private Map<Long, Long> findExistingIds(Long ownerUserId, String acctId, List<Long> fids) {
        Map<Long, Long> result = new HashMap<>();
        for (List<Long> chunk : ListUtil.partition(fids, IN_CHUNK)) {
            List<KdCustomerPo> list = customerMapper.selectList(new LambdaQueryWrapper<KdCustomerPo>()
                    .select(KdCustomerPo::getId, KdCustomerPo::getKdFid)
                    .eq(KdCustomerPo::getOwnerUserId, ownerUserId)
                    .eq(KdCustomerPo::getAcctId, acctId)
                    .in(KdCustomerPo::getKdFid, chunk));
            for (KdCustomerPo po : list) {
                result.put(po.getKdFid(), po.getId());
            }
        }
        return result;
    }
}
