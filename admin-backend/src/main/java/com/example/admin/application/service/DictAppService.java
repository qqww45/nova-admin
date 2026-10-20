package com.example.admin.application.service;

import com.example.admin.interfaces.dto.resp.DictResp;

import java.util.List;

/**
 * 业务字典应用服务（税率等禁止前端硬编码）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface DictAppService {

    /**
     * 按字典编码查询。
     *
     * @param dictCode 字典编码
     * @return 字典项
     */
    List<DictResp> listByCode(String dictCode);

    /**
     * 查询全部字典（前端一次性缓存，避免每个下拉都发一次请求）。
     *
     * @return 字典项
     */
    List<DictResp> listAll();
}
