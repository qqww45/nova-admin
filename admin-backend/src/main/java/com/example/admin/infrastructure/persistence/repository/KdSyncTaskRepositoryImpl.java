package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.kd.KdSyncTask;
import com.example.admin.domain.repository.KdSyncTaskRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdSyncTaskMapper;
import com.example.admin.infrastructure.persistence.po.KdSyncTaskPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步任务仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdSyncTaskRepositoryImpl implements KdSyncTaskRepository {

    private final KdSyncTaskMapper taskMapper;
    private final KdConverter converter;

    @Override
    public List<KdSyncTask> listAll() {
        return taskMapper.selectList(new LambdaQueryWrapper<KdSyncTaskPo>()
                        .orderByAsc(KdSyncTaskPo::getAcctId)
                        .orderByAsc(KdSyncTaskPo::getSortNo))
                .stream().map(converter::taskToDomain).toList();
    }

    @Override
    public List<KdSyncTask> listEffective(String acctId) {
        String acct = (acctId == null || acctId.isBlank()) ? KdSyncTask.GLOBAL : acctId.trim();
        List<KdSyncTaskPo> rows = taskMapper.selectList(new LambdaQueryWrapper<KdSyncTaskPo>()
                .and(w -> w.eq(KdSyncTaskPo::getAcctId, KdSyncTask.GLOBAL)
                        .or().eq(KdSyncTaskPo::getAcctId, acct))
                .eq(KdSyncTaskPo::getEnabled, 1)
                .orderByAsc(KdSyncTaskPo::getSortNo));
        // 先放全局模板，再用账套专属覆盖：同一个 entity 只保留一条
        Map<String, KdSyncTask> merged = new LinkedHashMap<>();
        for (KdSyncTaskPo po : rows) {
            merged.put(po.getEntity(), converter.taskToDomain(po));
        }
        List<KdSyncTask> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(t -> t.getSortNo() == null ? 0 : t.getSortNo()));
        return result;
    }

    @Override
    public KdSyncTask findById(Long id) {
        return id == null ? null : converter.taskToDomain(taskMapper.selectById(id));
    }

    @Override
    public KdSyncTask findByAcctAndEntity(String acctId, String entity) {
        KdSyncTaskPo po = taskMapper.selectOne(new LambdaQueryWrapper<KdSyncTaskPo>()
                .eq(KdSyncTaskPo::getAcctId, acctId)
                .eq(KdSyncTaskPo::getEntity, entity));
        return converter.taskToDomain(po);
    }

    @Override
    public Long save(KdSyncTask task) {
        KdSyncTaskPo po = converter.taskToPo(task);
        taskMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(KdSyncTask task) {
        taskMapper.updateById(converter.taskToPo(task));
    }
}
