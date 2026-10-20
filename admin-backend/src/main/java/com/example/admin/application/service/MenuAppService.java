package com.example.admin.application.service;

import com.example.admin.interfaces.dto.req.MenuSaveReq;
import com.example.admin.interfaces.dto.resp.MenuVo;

import java.util.List;

/**
 * 菜单应用服务（用例编排层）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface MenuAppService {

    /**
     * 查询菜单树（管理用，包含目录/菜单/按钮）。
     *
     * @param menuName 菜单名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 菜单树
     */
    List<MenuVo> tree(String menuName, Integer status);

    /**
     * 构建当前用户的菜单树（前端动态路由数据源）。
     *
     * @param userId 用户 ID
     * @return 菜单树
     */
    List<MenuVo> buildUserMenuTree(Long userId);

    /**
     * 查询角色已分配的菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 集合
     */
    List<String> selectMenuIdsByRoleId(Long roleId);

    /**
     * 新增菜单。
     *
     * @param req 菜单参数
     * @return 新菜单 ID
     */
    String create(MenuSaveReq req);

    /**
     * 修改菜单。
     *
     * @param req 菜单参数
     */
    void update(MenuSaveReq req);

    /**
     * 批量删除菜单（含子菜单）。
     *
     * @param ids 菜单 ID 集合
     */
    void remove(List<String> ids);
}
