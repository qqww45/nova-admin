package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdUpsertResult;
import com.example.admin.domain.repository.KdOutboundRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundLineMapper;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundMapper;
import com.example.admin.infrastructure.persistence.po.KdOutboundLinePo;
import com.example.admin.infrastructure.persistence.po.KdOutboundPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 金蝶出库单头仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdOutboundRepositoryImpl implements KdOutboundRepository {

    private static final int IN_CHUNK = 500;

    private final KdOutboundMapper outboundMapper;
    private final KdOutboundLineMapper lineMapper;
    private final KdConverter converter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KdUpsertResult upsertAll(Long ownerUserId, String acctId, String batchNo, List<KdOutbound> rows) {
        if (rows == null || rows.isEmpty()) {
            return KdUpsertResult.empty();
        }
        List<KdOutbound> valid = rows.stream()
                .filter(r -> r.getKdFid() != null && r.getKdFid() > 0)
                .toList();
        int skipped = rows.size() - valid.size();
        Map<Long, Long> existing = findExistingIds(ownerUserId, acctId,
                valid.stream().map(KdOutbound::getKdFid).distinct().toList());
        LocalDateTime now = LocalDateTime.now();
        int upserted = 0;
        for (KdOutbound row : valid) {
            KdOutboundPo po = converter.outboundToPo(row);
            po.setOwnerUserId(ownerUserId);
            po.setAcctId(acctId);
            po.setBatchNo(batchNo);
            po.setSyncAt(now);
            // 重新出现的单据必须清掉失效标记，否则"删除后又恢复"的单据永远查不到
            po.setInvalid(0);
            Long id = existing.get(row.getKdFid());
            if (id != null) {
                po.setId(id);
                outboundMapper.updateById(po);
            } else {
                outboundMapper.insert(po);
                existing.put(row.getKdFid(), po.getId());
            }
            upserted++;
        }
        return new KdUpsertResult(rows.size(), upserted, skipped);
    }

    @Override
    public int recalculateAmountByBatch(Long ownerUserId, String acctId, String batchNo) {
        List<KdOutboundLinePo> lines = lineMapper.selectList(new LambdaQueryWrapper<KdOutboundLinePo>()
                .select(KdOutboundLinePo::getOutboundFid, KdOutboundLinePo::getAmountCents)
                .eq(KdOutboundLinePo::getOwnerUserId, ownerUserId)
                .eq(KdOutboundLinePo::getAcctId, acctId)
                .eq(KdOutboundLinePo::getBatchNo, batchNo)
                .eq(KdOutboundLinePo::getInvalid, 0));
        if (lines.isEmpty()) {
            return 0;
        }
        Map<Long, Long> sums = new HashMap<>();
        for (KdOutboundLinePo line : lines) {
            long amount = line.getAmountCents() == null ? 0L : line.getAmountCents();
            sums.merge(line.getOutboundFid(), amount, Long::sum);
        }
        int updated = 0;
        for (Map.Entry<Long, Long> entry : sums.entrySet()) {
            updated += outboundMapper.update(null, new LambdaUpdateWrapper<KdOutboundPo>()
                    .eq(KdOutboundPo::getOwnerUserId, ownerUserId)
                    .eq(KdOutboundPo::getAcctId, acctId)
                    .eq(KdOutboundPo::getKdFid, entry.getKey())
                    .set(KdOutboundPo::getAmountCents, entry.getValue()));
        }
        return updated;
    }

    @Override
    public int markInvalidNotInBatch(Long ownerUserId, String acctId, String batchNo) {
        return outboundMapper.update(null, new LambdaUpdateWrapper<KdOutboundPo>()
                .eq(KdOutboundPo::getOwnerUserId, ownerUserId)
                .eq(KdOutboundPo::getAcctId, acctId)
                .eq(KdOutboundPo::getInvalid, 0)
                .and(w -> w.ne(KdOutboundPo::getBatchNo, batchNo).or().isNull(KdOutboundPo::getBatchNo))
                .set(KdOutboundPo::getInvalid, 1));
    }

    @Override
    public long countByOwner(Long ownerUserId, String acctId) {
        return outboundMapper.selectCount(new LambdaQueryWrapper<KdOutboundPo>()
                .eq(KdOutboundPo::getOwnerUserId, ownerUserId)
                .eq(KdOutboundPo::getAcctId, acctId)
                .eq(KdOutboundPo::getInvalid, 0));
    }

    /**
     * 批量查已存在的主键。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param fids 金蝶单据 FID
     * @return kdFid → 主键
     */
    private Map<Long, Long> findExistingIds(Long ownerUserId, String acctId, List<Long> fids) {
        Map<Long, Long> result = new HashMap<>();
        for (List<Long> chunk : ListUtil.partition(fids, IN_CHUNK)) {
            List<KdOutboundPo> list = outboundMapper.selectList(new LambdaQueryWrapper<KdOutboundPo>()
                    .select(KdOutboundPo::getId, KdOutboundPo::getKdFid)
                    .eq(KdOutboundPo::getOwnerUserId, ownerUserId)
                    .eq(KdOutboundPo::getAcctId, acctId)
                    .in(KdOutboundPo::getKdFid, chunk));
            for (KdOutboundPo po : list) {
                result.put(po.getKdFid(), po.getId());
            }
        }
        return result;
    }
}
