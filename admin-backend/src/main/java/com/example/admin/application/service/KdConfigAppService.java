package com.example.admin.application.service;

import com.example.admin.interfaces.dto.req.KdFieldMappingSaveReq;
import com.example.admin.interfaces.dto.req.KdTaskSaveReq;
import com.example.admin.interfaces.dto.resp.KdFieldMappingResp;
import com.example.admin.interfaces.dto.resp.KdSyncTaskResp;

import java.util.List;

/**
 * 同步配置应用服务：任务清单 + 账套字段映射。
 *
 * <p>这两张表是"改字段不发版"的落点：客户端每次启动 / 收到 config.reload 就重拉一次。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdConfigAppService {

    /**
     * 查询全部同步任务（含全局模板与账套覆盖）。
     *
     * @return 任务列表
     */
    List<KdSyncTaskResp> listTasks();

    /**
     * 保存任务（按 acctId + entity 唯一，已存在则更新）。
     *
     * @param req 请求
     * @return 任务 ID
     */
    String saveTask(KdTaskSaveReq req);

    /**
     * 查询某绑定的字段映射。
     *
     * @param bindingId 绑定 ID
     * @return 映射列表
     */
    List<KdFieldMappingResp> listMappings(String bindingId);

    /**
     * 保存字段映射（按 bindingId + mappingKey 唯一，已存在则更新）。
     *
     * @param req 请求
     */
    void saveMapping(KdFieldMappingSaveReq req);
}
