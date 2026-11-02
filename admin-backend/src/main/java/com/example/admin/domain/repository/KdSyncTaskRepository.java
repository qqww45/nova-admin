package com.example.admin.domain.repository;

import com.example.admin.domain.model.kd.KdSyncTask;

import java.util.List;

/**
 * 同步任务仓储。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdSyncTaskRepository {

    /**
     * 查询全部任务（管理页面）。
     *
     * @return 任务列表
     */
    List<KdSyncTask> listAll();

    /**
     * 查询某账套实际生效的任务（全局模板 + 账套覆盖，账套优先）。
     *
     * @param acctId 账套 ID
     * @return 生效任务列表
     */
    List<KdSyncTask> listEffective(String acctId);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 任务；不存在返回 null
     */
    KdSyncTask findById(Long id);

    /**
     * 按账套 + 实体查询。
     *
     * @param acctId 账套（* 表示全局）
     * @param entity 实体
     * @return 任务；不存在返回 null
     */
    KdSyncTask findByAcctAndEntity(String acctId, String entity);

    /**
     * 新增任务。
     *
     * @param task 任务
     * @return 新主键
     */
    Long save(KdSyncTask task);

    /**
     * 更新任务。
     *
     * @param task 任务
     */
    void update(KdSyncTask task);
}
