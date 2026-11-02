package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.model.kd.KdUpsertResult;
import com.example.admin.domain.repository.KdOutboundLineRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundLineMapper;
import com.example.admin.infrastructure.persistence.po.KdOutboundLinePo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 金蝶出库单分录仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdOutboundLineRepositoryImpl implements KdOutboundLineRepository {

    private static final int IN_CHUNK = 500;

    private final KdOutboundLineMapper lineMapper;
    private final KdConverter converter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KdUpsertResult upsertAll(Long ownerUserId, String acctId, String batchNo, List<KdOutboundLine> rows) {
        if (rows == null || rows.isEmpty()) {
            return KdUpsertResult.empty();
        }
        List<KdOutboundLine> valid = rows.stream()
                .filter(r -> r.getEntryId() != null && r.getEntryId() > 0
                        && r.getOutboundFid() != null && r.getOutboundFid() > 0)
                .toList();
        int skipped = rows.size() - valid.size();
        Map<Long, Long> existing = findExistingIds(ownerUserId, acctId,
                valid.stream().map(KdOutboundLine::getEntryId).distinct().toList());
        LocalDateTime now = LocalDateTime.now();
        int upserted = 0;
        for (KdOutboundLine row : valid) {
            KdOutboundLinePo po = converter.lineToPo(row);
            po.setOwnerUserId(ownerUserId);
            po.setAcctId(acctId);
            po.setBatchNo(batchNo);
            po.setSyncAt(now);
            po.setInvalid(0);
            Long id = existing.get(row.getEntryId());
            if (id != null) {
                po.setId(id);
                lineMapper.updateById(po);
            } else {
                lineMapper.insert(po);
                existing.put(row.getEntryId(), po.getId());
            }
            upserted++;
        }
        return new KdUpsertResult(rows.size(), upserted, skipped);
    }

    @Override
    public int markInvalidNotInBatch(Long ownerUserId, String acctId, String batchNo) {
        return lineMapper.update(null, new LambdaUpdateWrapper<KdOutboundLinePo>()
                .eq(KdOutboundLinePo::getOwnerUserId, ownerUserId)
                .eq(KdOutboundLinePo::getAcctId, acctId)
                .eq(KdOutboundLinePo::getInvalid, 0)
                .and(w -> w.ne(KdOutboundLinePo::getBatchNo, batchNo)
                        .or().isNull(KdOutboundLinePo::getBatchNo))
                .set(KdOutboundLinePo::getInvalid, 1));
    }

    @Override
    public long countByOwner(Long ownerUserId, String acctId) {
        return lineMapper.selectCount(new LambdaQueryWrapper<KdOutboundLinePo>()
                .eq(KdOutboundLinePo::getOwnerUserId, ownerUserId)
                .eq(KdOutboundLinePo::getAcctId, acctId)
                .eq(KdOutboundLinePo::getInvalid, 0));
    }

    /**
     * 批量查已存在的主键。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param entryIds 分录内码
     * @return entryId → 主键
     */
    private Map<Long, Long> findExistingIds(Long ownerUserId, String acctId, List<Long> entryIds) {
        Map<Long, Long> result = new HashMap<>();
        for (List<Long> chunk : ListUtil.partition(entryIds, IN_CHUNK)) {
            List<KdOutboundLinePo> list = lineMapper.selectList(new LambdaQueryWrapper<KdOutboundLinePo>()
                    .select(KdOutboundLinePo::getId, KdOutboundLinePo::getEntryId)
                    .eq(KdOutboundLinePo::getOwnerUserId, ownerUserId)
                    .eq(KdOutboundLinePo::getAcctId, acctId)
                    .in(KdOutboundLinePo::getEntryId, chunk));
            for (KdOutboundLinePo po : list) {
                result.put(po.getEntryId(), po.getId());
            }
        }
        return result;
    }
}
