package com.example.admin.application.service;

import com.example.admin.interfaces.dto.req.DeptSaveReq;
import com.example.admin.interfaces.dto.resp.DeptVo;

import java.util.List;

/**
 * 部门应用服务（用例编排层）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface DeptAppService {

    /**
     * 查询部门树。
     *
     * @param deptName 部门名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 部门树
     */
    List<DeptVo> tree(String deptName, Integer status);

    /**
     * 新增部门。
     *
     * @param req 部门参数
     * @return 新部门 ID
     */
    String create(DeptSaveReq req);

    /**
     * 修改部门。
     *
     * @param req 部门参数
     */
    void update(DeptSaveReq req);

    /**
     * 批量删除部门（含子部门）。
     *
     * @param ids 部门 ID 集合
     */
    void remove(List<String> ids);

    /**
     * 获取部门名称（列表展示与用户详情用）。
     *
     * @param deptId 部门 ID
     * @return 部门名称，不存在返回空串
     */
    String getDeptName(Long deptId);
}
