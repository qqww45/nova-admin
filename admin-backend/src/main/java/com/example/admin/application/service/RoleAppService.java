package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.AssignMenuReq;
import com.example.admin.interfaces.dto.req.RolePageQuery;
import com.example.admin.interfaces.dto.req.RoleSaveReq;
import com.example.admin.interfaces.dto.req.UpdateStatusReq;
import com.example.admin.interfaces.dto.resp.RoleOptionResp;
import com.example.admin.interfaces.dto.resp.RoleResp;

import java.util.List;

/**
 * 角色应用服务（用例编排层）：角色 CRUD 与菜单权限分配。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface RoleAppService {

    /**
     * 分页查询角色。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<RoleResp> pageRoles(RolePageQuery query);

    /**
     * 查询角色详情（含已分配菜单）。
     *
     * @param id 角色 ID
     * @return 角色详情
     */
    RoleResp getRoleDetail(String id);

    /**
     * 查询角色下拉选项。
     *
     * @return 角色选项列表
     */
    List<RoleOptionResp> listOptions();

    /**
     * 新增角色（可同时分配菜单）。
     *
     * @param req 角色参数
     * @return 新角色 ID
     */
    String createRole(RoleSaveReq req);

    /**
     * 修改角色（menuIds 非空时同步权限）。
     *
     * @param req 角色参数
     */
    void updateRole(RoleSaveReq req);

    /**
     * 批量删除角色。
     *
     * @param ids 角色 ID 集合
     */
    void removeRoles(List<String> ids);

    /**
     * 修改角色状态。
     *
     * @param req 状态参数
     */
    void updateStatus(UpdateStatusReq req);

    /**
     * 给角色分配菜单权限。
     *
     * @param req 分配参数
     */
    void assignMenus(AssignMenuReq req);
}
