package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdBinding;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集器绑定仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdBindingRepository {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 绑定；不存在返回 null
     */
    KdBinding findById(Long id);

    /**
     * 按令牌摘要查询（鉴权入口）。
     *
     * @param tokenHash 令牌 SHA-256
     * @return 绑定；不存在返回 null
     */
    KdBinding findByTokenHash(String tokenHash);

    /**
     * 按用户 + 账套查询。
     *
     * @param userId 用户 ID
     * @param acctId 账套 ID
     * @return 绑定；不存在返回 null
     */
    KdBinding findByUserAndAcct(Long userId, String acctId);

    /**
     * 查询某用户的全部绑定（含已吊销）。
     *
     * @param userId 用户 ID；null 表示不限（管理员）
     * @return 绑定列表
     */
    List<KdBinding> listByUser(Long userId);

    /**
     * 新增绑定。
     *
     * @param binding 绑定
     * @return 新主键
     */
    Long save(KdBinding binding);

    /**
     * 更新绑定（按主键全量更新非空字段）。
     *
     * @param binding 绑定
     */
    void update(KdBinding binding);

    /**
     * 记录令牌使用时间（同一分钟内只写一次，避免每个请求都产生一次 UPDATE）。
     *
     * @param id 绑定 ID
     * @param now 当前时间
     */
    void touch(Long id, LocalDateTime now);
}
