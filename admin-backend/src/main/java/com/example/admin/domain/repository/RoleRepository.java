package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.role.RoleQuery;

import java.util.Collection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 角色仓储接口（领域层定义）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface RoleRepository {

    /**
     * 按 ID 查询角色（含菜单权限关联）。
     *
     * @param id 角色 ID
     * @return 角色聚合
     */
    Optional<Role> findById(Long id);

    /**
     * 分页条件查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Role> search(RoleQuery query);

    /**
     * 查询全部角色（下拉选项等场景）。
     *
     * @return 角色列表
     */
    List<Role> findAll();

    /**
     * 按 ID 集合查询角色。
     *
     * @param ids 角色 ID 集合
     * @return 角色列表
     */
    List<Role> findByIds(Collection<Long> ids);

    /**
     * 保存角色聚合（含菜单权限关联同步）。
     *
     * @param role 角色聚合根
     * @return 保存后的聚合
     */
    Role save(Role role);

    /**
     * 逻辑删除（批量），同时清理角色-菜单关联。
     *
     * @param ids 角色 ID 集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 判断角色标识是否已存在。
     *
     * @param roleKey   角色标识
     * @param excludeId 排除的角色 ID
     * @return 是否已存在
     */
    boolean existsByRoleKey(String roleKey, Long excludeId);

    /**
     * 查询角色已分配的菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 集合
     */
    List<Long> findMenuIdsByRoleId(Long roleId);

    /**
     * 统计角色总数。
     *
     * @return 总数
     */
    long count();

    /**
     * 统计角色已分配给多少用户（删除前校验用）。
     *
     * @param roleId 角色 ID
     * @return 用户数
     */
    long countUsersByRoleId(Long roleId);

    /**
     * 批量统计各角色关联的用户数（一次 GROUP BY 取代循环里的 N 次 count）。
     *
     * @param roleIds 角色 ID 集合
     * @return 角色 ID → 用户数（无关联用户的角色不在结果中）
     */
    Map<Long, Long> countUsersByRoleIds(Collection<Long> roleIds);
}
