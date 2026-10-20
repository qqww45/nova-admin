package com.example.admin.domain.repository;

import com.example.admin.domain.model.menu.Menu;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 菜单仓储接口（领域层定义）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface MenuRepository {

    /**
     * 按 ID 查询菜单。
     *
     * @param id 菜单 ID
     * @return 菜单实体
     */
    Optional<Menu> findById(Long id);

    /**
     * 条件查询全部菜单（平铺）。
     *
     * @param menuName 菜单名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 菜单列表
     */
    List<Menu> findAll(String menuName, Integer status);

    /**
     * 按 ID 集合查询菜单。
     *
     * @param ids 菜单 ID 集合
     * @return 菜单列表
     */
    List<Menu> findByIds(Collection<Long> ids);

    /**
     * 查询指定角色集合拥有的菜单。
     *
     * @param roleIds 角色 ID 集合
     * @return 菜单列表
     */
    List<Menu> findByRoleIds(Collection<Long> roleIds);

    /**
     * 保存菜单（新增或更新）。
     *
     * @param menu 菜单实体
     * @return 保存后的菜单
     */
    Menu save(Menu menu);

    /**
     * 逻辑删除（批量），同时清理角色-菜单关联。
     *
     * @param ids 菜单 ID 集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 查询角色已分配的菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 集合
     */
    List<Long> findMenuIdsByRoleId(Long roleId);

    /**
     * 统计菜单总数。
     *
     * @return 总数
     */
    long count();
}
