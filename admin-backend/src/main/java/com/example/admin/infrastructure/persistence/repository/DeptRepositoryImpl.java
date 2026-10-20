package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.admin.domain.model.dept.Dept;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.infrastructure.persistence.converter.DeptConverter;
import com.example.admin.infrastructure.persistence.mapper.SysDeptMapper;
import com.example.admin.infrastructure.persistence.mapper.SysUserMapper;
import com.example.admin.infrastructure.persistence.po.SysDept;
import com.example.admin.infrastructure.persistence.po.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 部门仓储实现（Infrastructure 层）。
 *
 * <p>树形结构同样由领域层构建，仓储只提供平铺数据。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@RequiredArgsConstructor
public class DeptRepositoryImpl implements DeptRepository {

    private final SysDeptMapper deptMapper;

    private final SysUserMapper userMapper;

    private final DeptConverter deptConverter;

    /**
     * 按 ID 查询部门。
     *
     * @param id 部门 ID
     * @return 部门聚合
     */
    @Override
    public Optional<Dept> findById(Long id) {
        return Optional.ofNullable(deptConverter.toDomain(deptMapper.selectById(id)));
    }

    /**
     * 条件查询全部部门（平铺）。
     *
     * @param deptName 部门名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 部门列表
     */
    @Override
    public List<Dept> findAll(String deptName, Integer status) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<SysDept>()
                .like(StrUtil.isNotBlank(deptName), SysDept::getDeptName, deptName)
                .eq(status != null, SysDept::getStatus, status)
                .orderByAsc(SysDept::getParentId)
                .orderByAsc(SysDept::getSort);
        return deptMapper.selectList(wrapper).stream().map(deptConverter::toDomain).collect(Collectors.toList());
    }

    /**
     * 保存部门（新增或更新）。
     *
     * @param dept 部门聚合根
     * @return 保存后的部门
     */
    @Override
    public Dept save(Dept dept) {
        SysDept po = deptConverter.toPo(dept);
        if (dept.getId() == null) {
            deptMapper.insert(po);
            dept.setId(po.getId());
        } else {
            deptMapper.updateById(po);
        }
        return dept;
    }

    /**
     * 逻辑删除（批量）。
     *
     * @param ids 部门 ID 集合
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        deptMapper.deleteBatchIds(ids);
    }

    /**
     * 查询部门名称。
     *
     * @param id 部门 ID
     * @return 部门名称，不存在返回空串
     */
    @Override
    public String findNameById(Long id) {
        if (id == null) {
            return "";
        }
        SysDept dept = deptMapper.selectById(id);
        return dept == null ? "" : dept.getDeptName();
    }

    @Override
    public Map<Long, Long> countUsersByDeptIds(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = deptIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        // 一次 GROUP BY 取代循环里的 N 次 count（批量删除部门时的主要开销）
        List<Map<String, Object>> rows = userMapper.selectMaps(new QueryWrapper<SysUser>()
                .select("dept_id AS deptId", "COUNT(*) AS cnt")
                .in("dept_id", distinct)
                .groupBy("dept_id"));
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object deptId = row.get("deptId");
            if (deptId == null) {
                continue;
            }
            Object cnt = row.get("cnt");
            map.put(((Number) deptId).longValue(), cnt == null ? 0L : ((Number) cnt).longValue());
        }
        return map;
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (SysDept dept : deptMapper.selectBatchIds(distinct)) {
            if (dept != null && dept.getId() != null) {
                map.put(dept.getId(), dept.getDeptName() == null ? "" : dept.getDeptName());
            }
        }
        return map;
    }

    /**
     * 统计部门总数。
     *
     * @return 总数
     */
    @Override
    public long count() {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeleted, 0));
        return count == null ? 0L : count;
    }

    /**
     * 统计部门下的用户数。
     *
     * @param deptId 部门 ID
     * @return 用户数
     */
    @Override
    public long countUsersByDeptId(Long deptId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, deptId));
        return count == null ? 0L : count;
    }
}
