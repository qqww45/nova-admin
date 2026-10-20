package com.example.admin.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 通用树构建领域服务（菜单树、部门树共用）。
 *
 * <p>为什么放在领域层：树形结构是业务概念（组织架构、菜单层级），
 * 且该算法不依赖任何技术框架；放在这里可被菜单、部门、未来的字典/分类模块复用。
 *
 * <p>算法：先按父 ID 建立索引再挂载子节点，时间复杂度 O(n)，
 * 优于"每个节点遍历全表找子节点"的 O(n²) 写法。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class TreeDomainService {

    /** 私有构造，纯静态工具类 */
    private TreeDomainService() {
    }

    /**
     * 将平铺列表构建为树形结构。
     *
     * <p>用法示例：
     * <pre>{@code
     *     List<Dept> tree = TreeDomainService.build(depts,
     *             Dept::getId, Dept::getParentId, Dept::setChildren,
     *             Comparator.comparing(d -> d.getSort() == null ? 0 : d.getSort()));
     * }</pre>
     *
     * @param nodes           节点列表
     * @param idGetter        节点 ID 获取器
     * @param parentIdGetter  父节点 ID 获取器
     * @param childrenSetter  子节点设置器
     * @param comparator      同级排序器（可为 null，表示保持原顺序）
     * @param <T>             节点类型
     * @param <ID>            ID 类型
     * @return 根节点列表（父 ID 为空 / 为 0 / 不在列表中的节点视为根）
     */
    public static <T, ID> List<T> build(List<T> nodes,
                                        Function<T, ID> idGetter,
                                        Function<T, ID> parentIdGetter,
                                        BiConsumer<T, List<T>> childrenSetter,
                                        Comparator<T> comparator) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        // 1. 建立 父ID → 子节点列表 索引
        Map<ID, List<T>> childrenMap = new HashMap<>();
        for (T node : nodes) {
            childrenMap.computeIfAbsent(parentIdGetter.apply(node), key -> new ArrayList<>()).add(node);
        }
        // 2. 清空旧引用后挂载子节点（避免重复调用产生脏数据）
        for (T node : nodes) {
            childrenSetter.accept(node, new ArrayList<>());
        }
        for (T node : nodes) {
            List<T> children = childrenMap.getOrDefault(idGetter.apply(node), new ArrayList<>());
            if (!children.isEmpty()) {
                if (comparator != null) {
                    children.sort(comparator);
                }
                childrenSetter.accept(node, children);
            }
        }
        // 3. 识别根节点
        List<T> roots = new ArrayList<>();
        for (T node : nodes) {
            ID parentId = parentIdGetter.apply(node);
            if (parentId == null || isZero(parentId) || !containsId(nodes, idGetter, parentId)) {
                roots.add(node);
            }
        }
        if (comparator != null) {
            roots.sort(comparator);
        }
        return roots;
    }

    /**
     * 判断 ID 是否为顶级标识（数值 0 或字符串 "0"）。
     *
     * @param id ID
     * @return 是否顶级
     */
    private static boolean isZero(Object id) {
        return Objects.equals(id, 0L) || Objects.equals(id, 0) || Objects.equals(id, "0");
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
            if (Objects.equals(idGetter.apply(node), id)) {
                return true;
            }
        }
        return false;
    }
}
