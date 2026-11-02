package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.kd.KdBatchQuery;
import com.example.admin.domain.model.kd.KdSyncBatch;
import com.example.admin.domain.repository.KdSyncBatchRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdSyncBatchMapper;
import com.example.admin.infrastructure.persistence.po.KdSyncBatchPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 同步批次仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdSyncBatchRepositoryImpl implements KdSyncBatchRepository {

    private final KdSyncBatchMapper batchMapper;
    private final KdConverter converter;

    @Override
    public KdSyncBatch findByBatchNo(String batchNo) {
        if (StrUtil.isBlank(batchNo)) {
            return null;
        }
        KdSyncBatchPo po = batchMapper.selectOne(new LambdaQueryWrapper<KdSyncBatchPo>()
                .eq(KdSyncBatchPo::getBatchNo, batchNo));
        return converter.batchToDomain(po);
    }

    @Override
    public Long save(KdSyncBatch batch) {
        KdSyncBatchPo po = converter.batchToPo(batch);
        batchMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(KdSyncBatch batch) {
        batchMapper.updateById(converter.batchToPo(batch));
    }

    @Override
    public PageResult<KdSyncBatch> page(KdBatchQuery query) {
        LambdaQueryWrapper<KdSyncBatchPo> wrapper = new LambdaQueryWrapper<KdSyncBatchPo>()
                .eq(query.getOwnerUserId() != null, KdSyncBatchPo::getOwnerUserId, query.getOwnerUserId())
                .eq(StrUtil.isNotBlank(query.getEntity()), KdSyncBatchPo::getEntity, query.getEntity())
                .eq(StrUtil.isNotBlank(query.getStatus()), KdSyncBatchPo::getStatus, query.getStatus())
                .eq(StrUtil.isNotBlank(query.getAcctId()), KdSyncBatchPo::getAcctId, query.getAcctId())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(KdSyncBatchPo::getBatchNo, query.getKeyword())
                        .or().like(KdSyncBatchPo::getMessage, query.getKeyword()))
                .orderByDesc(KdSyncBatchPo::getCreateTime);
        IPage<KdSyncBatchPo> page = batchMapper.selectPage(query.buildPage(), wrapper);
        List<KdSyncBatch> records = page.getRecords().stream().map(converter::batchToDomain).toList();
        PageResult<KdSyncBatch> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
