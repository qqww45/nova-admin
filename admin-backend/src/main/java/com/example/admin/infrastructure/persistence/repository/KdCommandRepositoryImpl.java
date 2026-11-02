package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.admin.domain.model.kd.KdCommand;
import com.example.admin.domain.repository.KdCommandRepository;
import com.example.admin.infrastructure.persistence.mapper.KdCommandMapper;
import com.example.admin.infrastructure.persistence.po.KdCommandPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集器指令仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdCommandRepositoryImpl implements KdCommandRepository {

    private final KdCommandMapper commandMapper;

    @Override
    public Long save(KdCommand command) {
        KdCommandPo po = toPo(command);
        commandMapper.insert(po);
        return po.getId();
    }

    @Override
    public KdCommand findPending(Long bindingId) {
        List<KdCommandPo> list = commandMapper.selectList(new LambdaQueryWrapper<KdCommandPo>()
                .eq(KdCommandPo::getBindingId, bindingId)
                .eq(KdCommandPo::getStatus, KdCommand.PENDING)
                .orderByAsc(KdCommandPo::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : toDomain(list.get(0));
    }

    @Override
    public KdCommand findById(Long id) {
        KdCommandPo po = commandMapper.selectById(id);
        return po == null ? null : toDomain(po);
    }

    @Override
    public int markSent(Long id, LocalDateTime now) {
        // 带 status 条件更新：同一条指令被并发取走时只有一个请求能改成功
        return commandMapper.update(null, new LambdaUpdateWrapper<KdCommandPo>()
                .eq(KdCommandPo::getId, id)
                .eq(KdCommandPo::getStatus, KdCommand.PENDING)
                .set(KdCommandPo::getStatus, KdCommand.SENT)
                .set(KdCommandPo::getDeliverTime, now));
    }

    @Override
    public void markDone(Long id, String result, LocalDateTime now) {
        commandMapper.update(null, new LambdaUpdateWrapper<KdCommandPo>()
                .eq(KdCommandPo::getId, id)
                .set(KdCommandPo::getStatus, KdCommand.DONE)
                .set(KdCommandPo::getResult, result)
                .set(KdCommandPo::getDoneTime, now));
    }

    @Override
    public int expireBefore(Long bindingId, LocalDateTime before) {
        return commandMapper.update(null, new LambdaUpdateWrapper<KdCommandPo>()
                .eq(KdCommandPo::getBindingId, bindingId)
                .eq(KdCommandPo::getStatus, KdCommand.PENDING)
                .lt(KdCommandPo::getCreateTime, before)
                .set(KdCommandPo::getStatus, KdCommand.EXPIRED));
    }

    @Override
    public List<KdCommand> listByBinding(Long bindingId, int limit) {
        return commandMapper.selectList(new LambdaQueryWrapper<KdCommandPo>()
                        .eq(KdCommandPo::getBindingId, bindingId)
                        .orderByDesc(KdCommandPo::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))))
                .stream().map(this::toDomain).toList();
    }

    /**
     * PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    private KdCommand toDomain(KdCommandPo po) {
        KdCommand command = new KdCommand();
        command.setId(po.getId());
        command.setBindingId(po.getBindingId());
        command.setUserId(po.getUserId());
        command.setAcctId(po.getAcctId());
        command.setCommand(po.getCommand());
        command.setPayload(po.getPayload());
        command.setStatus(po.getStatus());
        command.setResult(po.getResult());
        command.setCreateTime(po.getCreateTime());
        command.setDeliverTime(po.getDeliverTime());
        command.setDoneTime(po.getDoneTime());
        command.setExpireTime(po.getExpireTime());
        return command;
    }

    /**
     * 领域模型 → PO。
     *
     * @param command 领域模型
     * @return 持久化对象
     */
    private KdCommandPo toPo(KdCommand command) {
        KdCommandPo po = new KdCommandPo();
        po.setId(command.getId());
        po.setBindingId(command.getBindingId());
        po.setUserId(command.getUserId());
        po.setAcctId(command.getAcctId());
        po.setCommand(command.getCommand());
        po.setPayload(command.getPayload());
        po.setStatus(command.getStatus());
        po.setResult(command.getResult());
        po.setDeliverTime(command.getDeliverTime());
        po.setDoneTime(command.getDoneTime());
        po.setExpireTime(command.getExpireTime());
        return po;
    }
}
