package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdCommand;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集器指令仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdCommandRepository {

    /**
     * 入队。
     *
     * @param command 指令
     * @return 主键
     */
    Long save(KdCommand command);

    /**
     * 取该绑定最老的一条待取指令（取不到返回 null）。
     *
     * @param bindingId 绑定 ID
     * @return 指令
     */
    KdCommand findPending(Long bindingId);

    /**
     * 按主键查（回执时校验归属）。
     *
     * @param id 指令 ID
     * @return 指令，不存在返回 null
     */
    KdCommand findById(Long id);

    /**
     * 标记已被取走。
     *
     * @param id 指令 ID
     * @param now 时间
     * @return 影响行数（0 表示被别的连接抢走了）
     */
    int markSent(Long id, LocalDateTime now);

    /**
     * 标记执行完成。
     *
     * @param id 指令 ID
     * @param result 结果摘要
     * @param now 时间
     */
    void markDone(Long id, String result, LocalDateTime now);

    /**
     * 把过期未取的指令置为 EXPIRED。
     *
     * @param bindingId 绑定 ID
     * @param before 早于该时间仍未取走即过期
     * @return 影响行数
     */
    int expireBefore(Long bindingId, LocalDateTime before);

    /**
     * 最近指令（页面展示用）。
     *
     * @param bindingId 绑定 ID
     * @param limit 条数
     * @return 指令列表，按创建时间倒序
     */
    List<KdCommand> listByBinding(Long bindingId, int limit);
}
