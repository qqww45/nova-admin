package com.example.admin.domain.service;

import com.example.admin.domain.model.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 菜单树领域服务。
 *
 * <p>为什么是领域服务而不是实体方法：
 * "把平铺列表组装成树"是跨多个 Menu 实体的操作，不属于单个实体的职责，
 * 因此提升为领域服务（DDD 中领域服务承载"不适合放在实体上的领域操作"）。
 *
 * <p>算法：先按父 ID 建立索引再挂载子节点，时间复杂度 O(n)，
 * 优于"每个节点遍历全表找子节点"的 O(n²) 写法。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class MenuTreeDomainService {

    /**
     * 将平铺菜单列表构建为树（仅保留给定范围内的节点）。
     *
     * @param menus 平铺菜单列表
     * @return 根菜单列表（父节点不在列表中的节点视为根）
     */
    public List<Menu> buildTree(List<Menu> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }
        // 1. 建立 父ID → 子节点列表 索引
        Map<Long, List<Menu>> childrenMap = new HashMap<>();
        for (Menu menu : menus) {
            childrenMap.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
        }
        // 2. 清空旧的子节点引用并挂载（避免重复调用产生脏数据）
        for (Menu menu : menus) {
            menu.setChildren(new ArrayList<>());
        }
        for (Menu menu : menus) {
            List<Menu> children = childrenMap.get(menu.getId());
            if (children != null && !children.isEmpty()) {
                children.sort((a, b) -> Integer.compare(nullZero(a.getSort()), nullZero(b.getSort())));
                menu.setChildren(children);
            }
        }
        // 3. 父节点不在列表中的节点作为根
        List<Menu> roots = new ArrayList<>();
        for (Menu menu : menus) {
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0L || !containsId(menus, parentId)) {
                roots.add(menu);
            }
        }
        roots.sort((a, b) -> Integer.compare(nullZero(a.getSort()), nullZero(b.getSort())));
        return roots;
    }

    /**
     * 递归收集指定菜单的所有子孙 ID（含自身），用于级联删除与环路检测。
     *
     * @param menus     全量菜单（平铺）
     * @param parentIds 起始菜单 ID 集合
     * @return 全部相关菜单 ID
     */
    public List<Long> collectSubTreeIds(List<Menu> menus, List<Long> parentIds) {
        List<Long> result = new ArrayList<>(parentIds);
        Map<Long, List<Menu>> childrenMap = new HashMap<>();
        for (Menu menu : menus) {
            childrenMap.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
        }
        // 用"已访问集合"防环，而不是"最多下钻 10 层"：
        // 固定上限会在树深度超过 10 时静默丢弃第 11 层及以下的节点 —— 没有日志、没有异常，
        // 表现为"删了父菜单却留下孤儿子菜单""环路检测漏掉深层节点"这类极难复现的数据损坏。
        // visited 同样能挡住脏数据造成的环（A→B→A），且不会截断任何合法深度；
        // 顺带把 result.contains 的 O(n²) 查找降为 O(1)。
        Set<Long> visited = new HashSet<>(parentIds);
        List<Long> current = new ArrayList<>(parentIds);
        while (!current.isEmpty()) {
            List<Long> next = new ArrayList<>();
            for (Long id : current) {
                for (Menu child : childrenMap.getOrDefault(id, List.of())) {
                    if (visited.add(child.getId())) {
                        next.add(child.getId());
                    }
                }
            }
            result.addAll(next);
            current = next;
        }
        return result;
    }

    /**
     * 判断列表中是否存在指定 ID 的节点。
     *
     * @param menus 菜单列表
     * @param id    菜单 ID
     * @return 是否存在
     */
    private boolean containsId(List<Menu> menus, Long id) {
        for (Menu menu : menus) {
            if (Objects.equals(menu.getId(), id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 排序值空值保护。
     *
     * @param value 排序值
     * @return 非空排序值
     */
    private int nullZero(Integer value) {
        return value == null ? 0 : value;
    }
}
