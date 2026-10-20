package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.role.DataScope;
import com.example.admin.domain.model.role.DataScopeFilter;
import com.example.admin.domain.model.dept.Dept;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据权限策略：回答"这个人能看到哪些数据"。
 *
 * <p>为什么把这段从切面里抽出来：原先"解析可见范围"的逻辑只存在于
 * {@code DataScopeAspect} 里，因此也只能被列表查询用到 ——
 * 单条读写（查看某个用户、改某个用户、删某些用户、强制某人下线）完全没有范围约束。
 * 结果是：一个"只能看本部门"的管理员，列表里确实看不到别的部门的人，
 * 但只要直接传对方的 ID 就能读能改 —— 列表过滤只是障眼法。
 *
 * <p>抽成领域服务之后，列表继续走"过滤条件"，单条操作走 {@link #canAccessUser} 判定，
 * 两边共用同一套规则，不会出现"列表管得住、单条管不住"的裂缝。
 *
 * <p>多角色取"最宽松"：同时拥有"本部门"和"全部数据"两个角色时按"全部数据"处理，
 * 避免出现"权限越加越小"的反直觉行为（用户会以为加角色是提权）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class DataScopePolicy {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final DeptRepository deptRepository;

    /**
     * 解析当前用户的数据可见范围（列表查询用）。
     *
     * @param user 当前用户；为 null 时按最严格处理
     * @return 过滤条件
     */
    public DataScopeFilter filterFor(LoginUser user) {
        if (user == null) {
            // 无登录上下文（定时任务、内部调用）：按最严格处理，避免"无上下文 = 全量可见"的越权
            return DataScopeFilter.bySelf(null);
        }
        if (user.isSuperAdmin()) {
            // 超级管理员不受数据权限约束：运维排查跨部门问题必需
            return DataScopeFilter.unrestricted();
        }
        List<Long> roleIds = userRepository.findRoleIdsByUserId(user.getUserId());
        if (roleIds == null || roleIds.isEmpty()) {
            return DataScopeFilter.bySelf(user.getUserId());
        }
        List<Role> roles = roleRepository.findByIds(roleIds);
        DataScope scope = roles.stream()
                .map(role -> DataScope.of(role.getDataScope()))
                .reduce(DataScope.SELF, (a, b) -> breadth(a) >= breadth(b) ? a : b);
        return resolveFilter(scope, user);
    }

    /**
     * 判断操作者能否访问某个用户（单条操作用）。
     *
     * <p>判定失败一律拒绝（fail-close）：拿不到操作者身份、或目标信息缺失时，
     * 都不能当作"可以访问"。
     *
     * @param operator      操作者
     * @param targetUserId  目标用户 ID
     * @param targetDeptId  目标用户所属部门
     * @return 是否允许访问
     */
    public boolean canAccessUser(LoginUser operator, Long targetUserId, Long targetDeptId) {
        if (operator == null || targetUserId == null) {
            return false;
        }
        if (operator.isSuperAdmin()) {
            return true;
        }
        DataScopeFilter filter = filterFor(operator);
        if (filter.isUnrestricted()) {
            return true;
        }
        if (filter.getSelfUserId() != null) {
            return filter.getSelfUserId().equals(targetUserId);
        }
        Set<Long> deptIds = filter.getDeptIds();
        return deptIds != null && targetDeptId != null && deptIds.contains(targetDeptId);
    }

    /**
     * 把范围枚举翻译成具体的过滤条件。
     *
     * @param scope 数据范围
     * @param user  当前用户
     * @return 过滤条件
     */
    private DataScopeFilter resolveFilter(DataScope scope, LoginUser user) {
        return switch (scope) {
            case ALL -> DataScopeFilter.unrestricted();
            case SELF -> DataScopeFilter.bySelf(user.getUserId());
            case DEPT -> DataScopeFilter.byDept(
                    user.getDeptId() == null ? Set.of() : Set.of(user.getDeptId()));
            // 本部门及其所有子部门：部门表数据量小，一次取全表在内存里做递归，
            // 好过"每个部门查一次"的 N+1（且部门层级通常不超过 5 层）。
            case DEPT_AND_CHILD -> DataScopeFilter.byDept(subTreeIds(user.getDeptId()));
            // 待实现：需要 sys_role_dept 表与 RoleRepository.findDeptIdsByRoleIds
            case CUSTOM -> DataScopeFilter.byDept(
                    user.getDeptId() == null ? Set.of() : Set.of(user.getDeptId()));
        };
    }

    /**
     * 收集部门及其所有后代部门的 ID（广度优先）。
     *
     * <p>为什么在内存里递归而不是逐层查库：部门表通常只有几十到几百行，
     * 一次查全表再建父子索引，比"每到一个部门查一次子部门"少得多。
     *
     * <p>调用频率：只在登录用户缓存未命中、以及单条操作做范围判定时才会走到，
     * 不是每个请求都执行（缓存命中时整条链路都被跳过）。
     *
     * @param deptId 起始部门 ID
     * @return 该部门及其所有后代部门 ID（含自身）
     */
    private Set<Long> subTreeIds(Long deptId) {
        if (deptId == null) {
            return Set.of();
        }
        List<Dept> all = deptRepository.findAll(null, null);
        Map<Long, List<Long>> children = new HashMap<>();
        for (Dept dept : all) {
            if (dept.getParentId() != null && dept.getId() != null) {
                children.computeIfAbsent(dept.getParentId(), key -> new ArrayList<>()).add(dept.getId());
            }
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(deptId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            List<Long> kids = children.get(current);
            if (kids != null) {
                queue.addAll(kids);
            }
        }
        return visited;
    }

    /**
     * 范围的"宽松度"分值：越大越宽松（用于多角色取并集）。
     *
     * @param scope 数据范围
     * @return 分值
     */
    private static int breadth(DataScope scope) {
        return switch (scope) {
            case ALL -> 100;
            case CUSTOM -> 60;
            case DEPT_AND_CHILD -> 50;
            case DEPT -> 40;
            case SELF -> 10;
        };
    }
}
