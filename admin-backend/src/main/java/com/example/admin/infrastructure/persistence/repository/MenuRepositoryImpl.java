package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.menu.Menu;
import com.example.admin.domain.repository.MenuRepository;
import com.example.admin.infrastructure.persistence.converter.MenuConverter;
import com.example.admin.infrastructure.persistence.mapper.SysMenuMapper;
import com.example.admin.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.example.admin.infrastructure.persistence.po.SysMenu;
import com.example.admin.infrastructure.persistence.po.SysRoleMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 菜单仓储实现（Infrastructure 层）。
 *
 * <p>注意：菜单在数据库中是平铺存储，树形结构由领域服务在内存中构建，
 * 仓储只负责"取平铺数据"，不关心树。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

    private final SysMenuMapper menuMapper;

    private final SysRoleMenuMapper roleMenuMapper;

    private final MenuConverter menuConverter;

    /**
     * 按 ID 查询菜单。
     *
     * @param id 菜单 ID
     * @return 菜单实体
     */
    @Override
    public Optional<Menu> findById(Long id) {
        return Optional.ofNullable(menuConverter.toDomain(menuMapper.selectById(id)));
    }

    /**
     * 条件查询全部菜单（平铺）。
     *
     * @param menuName 菜单名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 菜单列表
     */
    @Override
    public List<Menu> findAll(String menuName, Integer status) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .like(StrUtil.isNotBlank(menuName), SysMenu::getMenuName, menuName)
                .eq(status != null, SysMenu::getStatus, status)
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSort);
        return menuMapper.selectList(wrapper).stream().map(menuConverter::toDomain).collect(Collectors.toList());
    }

    /**
     * 按 ID 集合查询菜单。
     *
     * @param ids 菜单 ID 集合
     * @return 菜单列表
     */
    @Override
    public List<Menu> findByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new ArrayList<>();
        }
        return menuMapper.selectBatchIds(ids).stream().map(menuConverter::toDomain).collect(Collectors.toList());
    }

    /**
     * 查询指定角色集合拥有的菜单。
     *
     * @param roleIds 角色 ID 集合
     * @return 菜单列表
     */
    @Override
    public List<Menu> findByRoleIds(Collection<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return new ArrayList<>();
        }
        List<SysRoleMenu> relations = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .in(SysRoleMenu::getRoleId, roleIds));
        List<Long> menuIds = relations.stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());
        if (CollUtil.isEmpty(menuIds)) {
            return new ArrayList<>();
        }
        return findByIds(menuIds);
    }

    /**
     * 保存菜单（新增或更新）。
     *
     * @param menu 菜单实体
     * @return 保存后的菜单
     */
    @Override
    public Menu save(Menu menu) {
        SysMenu po = menuConverter.toPo(menu);
        if (menu.getId() == null) {
            menuMapper.insert(po);
            menu.setId(po.getId());
        } else {
            menuMapper.updateById(po);
        }
        return menu;
    }

    /**
     * 逻辑删除（批量），同时清理角色-菜单关联。
     *
     * @param ids 菜单 ID 集合
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        for (Long id : ids) {
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        }
        menuMapper.deleteBatchIds(ids);
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
     * 统计菜单总数。
     *
     * @return 总数
     */
    @Override
    public long count() {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>());
        return count == null ? 0L : count;
    }
}
