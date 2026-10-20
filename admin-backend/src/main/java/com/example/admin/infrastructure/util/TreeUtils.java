package com.example.admin.infrastructure.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 通用树结构构建工具（菜单树、部门树等）。
 *
 * <p>为什么抽成通用方法：菜单与部门都需要“平铺列表 → 树形结构”，
 * 抽象后新增任何树形业务都只需一行调用，避免复制粘贴。
 *
 * <p>实现说明：先按父 ID 建立索引再挂载子节点，时间复杂度 O(n)，
 * 优于“每个节点遍历全表找子节点”的 O(n²) 写法。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TreeUtils {

    /**
     * 将平铺列表构建为树形结构。
     *
     * <p>用法示例：
     * <pre>{@code
     *     List<SysMenu> tree = TreeUtils.build(
     *             menuList,
     *             SysMenu::getId,
     *             SysMenu::getParentId,
     *             SysMenu::setChildren,
     *             Comparator.comparing(SysMenu::getSort)
     *     );
     * }</pre>
     *
     * @param nodes           节点列表
     * @param idGetter        节点 ID 获取器
     * @param parentIdGetter  父节点 ID 获取器
     * @param childrenSetter  子节点设置器
     * @param comparator      同级排序器（可为 null 表示保持原顺序）
     * @param <T>             节点类型
     * @param <ID>            ID 类型
     * @return 根节点列表（父 ID 不存在于列表中的节点视为根节点）
     */
    public static <T, ID> List<T> build(List<T> nodes,
                                        Function<T, ID> idGetter,
                                        Function<T, ID> parentIdGetter,
                                        BiConsumer<T, List<T>> childrenSetter,
                                        Comparator<T> comparator) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 建立 ID → 子节点列表 的索引
        Map<ID, List<T>> childrenMap = new HashMap<>();
        for (T node : nodes) {
            ID parentId = parentIdGetter.apply(node);
            childrenMap.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
        }

        // 2. 递归挂载子节点
        List<T> roots = new ArrayList<>();
        for (T node : nodes) {
            ID id = idGetter.apply(node);
            List<T> children = childrenMap.get(id);
            if (children != null && !children.isEmpty()) {
                if (comparator != null) {
                    children.sort(comparator);
                }
                childrenSetter.accept(node, children);
            } else {
                childrenSetter.accept(node, new ArrayList<>());
            }
        }

        // 3. 找出根节点：其父 ID 不在所有节点 ID 集合中，或父 ID 为 null
        for (T node : nodes) {
            ID parentId = parentIdGetter.apply(node);
            if (parentId == null || !containsId(nodes, idGetter, parentId)) {
                roots.add(node);
            }
        }
        if (comparator != null) {
            roots.sort(comparator);
        }
        return roots;
    }

    /**
     * 判断节点列表中是否存在指定 ID。
     *
     * @param nodes    节点列表
     * @param idGetter ID 获取器
     * @param id       待判断 ID
     * @param <T>      节点类型
     * @param <ID>     ID 类型
     * @return 是否存在
     */
    private static <T, ID> boolean containsId(List<T> nodes, Function<T, ID> idGetter, ID id) {
        for (T node : nodes) {
            ID nodeId = idGetter.apply(node);
            if (nodeId != null && nodeId.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
