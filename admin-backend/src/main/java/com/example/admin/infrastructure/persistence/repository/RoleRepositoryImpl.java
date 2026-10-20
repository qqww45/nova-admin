package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.role.RoleQuery;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.infrastructure.persistence.converter.RoleConverter;
import com.example.admin.infrastructure.persistence.mapper.SysRoleMapper;
import com.example.admin.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.example.admin.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.example.admin.infrastructure.persistence.po.SysRole;
import com.example.admin.infrastructure.persistence.po.SysRoleMenu;
import com.example.admin.infrastructure.persistence.po.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 角色仓储实现（Infrastructure 层）。
 *
 * <p>聚合一致性：保存角色时同步 role_menu 关联；
 * 删除角色时同时清理关联，避免产生孤儿权限数据。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final SysRoleMapper roleMapper;

    private final SysRoleMenuMapper roleMenuMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final RoleConverter roleConverter;

    /**
     * 按 ID 查询角色（含菜单权限）。
     *
     * @param id 角色 ID
     * @return 角色聚合
     */
    @Override
    public Optional<Role> findById(Long id) {
        SysRole po = roleMapper.selectById(id);
        return Optional.ofNullable(toDomainWithMenus(po));
    }

    /**
     * 分页条件查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<Role> search(RoleQuery query) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StrUtil.isNotBlank(query.getRoleName()), SysRole::getRoleName, query.getRoleName())
                .like(StrUtil.isNotBlank(query.getRoleKey()), SysRole::getRoleKey, query.getRoleKey())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .ge(query.getBeginTime() != null, SysRole::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysRole::getCreateTime, query.getEndTime())
                .orderByAsc(SysRole::getSort)
                .orderByDesc(SysRole::getCreateTime);

        IPage<SysRole> page = roleMapper.selectPage(query.buildPage(), wrapper);
        List<Role> records = page.getRecords().stream().map(roleConverter::toDomain).collect(Collectors.toList());

        PageResult<Role> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    /**
     * 查询全部角色。
     *
     * @return 角色列表
     */
    @Override
    public List<Role> findAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSort))
                .stream().map(roleConverter::toDomain).collect(Collectors.toList());
    }

    /**
     * 按 ID 集合查询角色。
     *
     * @param ids 角色 ID 集合
     * @return 角色列表
     */
    @Override
    public List<Role> findByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new ArrayList<>();
        }
        return roleMapper.selectBatchIds(ids).stream().map(roleConverter::toDomain).collect(Collectors.toList());
    }

    /**
     * 保存角色聚合（含菜单权限同步）。
     *
     * @param role 角色聚合根
     * @return 保存后的角色
     */
    @Override
    public Role save(Role role) {
        SysRole po = roleConverter.toPo(role);
        if (role.getId() == null) {
            roleMapper.insert(po);
            role.setId(po.getId());
        } else {
            roleMapper.updateById(po);
        }
        saveMenuRelations(role.getId(), role.getMenuIds());
        return role;
    }

    /**
     * 逻辑删除（批量），同时清理角色-菜单关联。
     *
     * @param ids 角色 ID 集合
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        for (Long id : ids) {
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        }
        roleMapper.deleteBatchIds(ids);
    }

    /**
     * 判断角色标识是否已存在。
     *
     * @param roleKey   角色标识
     * @param excludeId 排除的角色 ID
     * @return 是否已存在
     */
    @Override
    public boolean existsByRoleKey(String roleKey, Long excludeId) {
        if (StrUtil.isBlank(roleKey)) {
            return false;
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeId != null, SysRole::getId, excludeId));
        return count != null && count > 0;
    }

    /**
     * 查询角色已分配的菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 集合
     */
    @Override
    public List<Long> findMenuIdsByRoleId(Long roleId) {
        List<SysRoleMenu> relations = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        return relations.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    /**
     * 统计角色总数。
     *
     * @return 总数
     */
    @Override
    public long count() {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>());
        return count == null ? 0L : count;
    }

    /**
     * 统计角色已分配给多少用户。
     *
     * @param roleId 角色 ID
     * @return 用户数
     */
    @Override
    public long countUsersByRoleId(Long roleId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        return count == null ? 0L : count;
    }

    @Override
    public Map<Long, Long> countUsersByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = userRoleMapper.selectMaps(new QueryWrapper<SysUserRole>()
                .select("role_id AS roleId", "COUNT(*) AS cnt")
                .in("role_id", distinct)
                .groupBy("role_id"));
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object rid = row.get("roleId");
            if (rid == null) {
                continue;
            }
            Object cnt = row.get("cnt");
            map.put(((Number) rid).longValue(), cnt == null ? 0L : ((Number) cnt).longValue());
        }
        return map;
    }

    /**
     * PO 转领域模型并填充菜单权限。
     *
     * @param po 持久化对象
     * @return 角色聚合根
     */
    private Role toDomainWithMenus(SysRole po) {
        if (po == null) {
            return null;
        }
        Role role = roleConverter.toDomain(po);
        role.assignMenus(findMenuIdsByRoleId(po.getId()));
        return role;
    }

    /**
     * 保存角色-菜单关联（先删后插）。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 集合
     */
    private void saveMenuRelations(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (CollUtil.isEmpty(menuIds)) {
            return;
        }
        for (Long menuId : menuIds) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        }
    }
}
