package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdBindingCode;

import java.time.LocalDateTime;

/**
 * 一次性绑定码仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdBindingCodeRepository {

    /**
     * 按摘要查询绑定码。
     *
     * @param codeHash 摘要
     * @return 绑定码；不存在返回 null
     */
    KdBindingCode findByHash(String codeHash);

    /**
     * 保存绑定码。
     *
     * @param code 绑定码
     * @return 新主键
     */
    Long save(KdBindingCode code);

    /**
     * 标记已使用（同时作为"用后即焚"的乐观锁：只有 used_at 为空才更新成功）。
     *
     * @param id 主键
     * @param usedAt 使用时间
     * @return 是否抢占成功；false 表示已被并发使用
     */
    boolean markUsed(Long id, LocalDateTime usedAt);
}
