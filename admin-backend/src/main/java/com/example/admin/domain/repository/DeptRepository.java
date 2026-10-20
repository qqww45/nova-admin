package com.example.admin.domain.repository;

import com.example.admin.domain.model.dept.Dept;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 部门仓储接口（领域层定义）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface DeptRepository {

    /**
     * 按 ID 查询部门。
     *
     * @param id 部门 ID
     * @return 部门聚合
     */
    Optional<Dept> findById(Long id);

    /**
     * 条件查询全部部门（平铺）。
     *
     * @param deptName 部门名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 部门列表
     */
    List<Dept> findAll(String deptName, Integer status);

    /**
     * 保存部门（新增或更新）。
     *
     * @param dept 部门聚合根
     * @return 保存后的部门
     */
    Dept save(Dept dept);

    /**
     * 逻辑删除（批量）。
     *
     * @param ids 部门 ID 集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 查询部门名称（列表展示用）。
     *
     * @param id 部门 ID
     * @return 部门名称，不存在返回空串
     */
    String findNameById(Long id);

    /**
     * 批量查询部门名称。
     *
     * <p>存在理由：列表类接口若对每一行调用 {@link #findNameById}，
     * N 行就是 N 次查询（N+1），在线列表这类接口会随在线设备数线性劣化。
     *
     * @param ids 部门 ID 集合
     * @return 部门 ID → 部门名称（不存在的 ID 不在结果中）
     */
    Map<Long, String> findNamesByIds(Collection<Long> ids);

    /**
     * 批量统计各部门下的用户数。
     *
     * <p>存在理由：批量删除时若在循环里逐条 {@code countUsersByDeptId}，
     * 删 N 个部门就是 N 次查询；这里用一次 GROUP BY 拿回全部。
     *
     * @param deptIds 部门 ID 集合
     * @return 部门 ID → 用户数（无用户的部门不在结果中）
     */
    Map<Long, Long> countUsersByDeptIds(Collection<Long> deptIds);

    /**
     * 统计部门总数。
     *
     * @return 总数
     */
    long count();

    /**
     * 统计部门下的用户数（删除前校验用）。
     *
     * @param deptId 部门 ID
     * @return 用户数
     */
    long countUsersByDeptId(Long deptId);
}
