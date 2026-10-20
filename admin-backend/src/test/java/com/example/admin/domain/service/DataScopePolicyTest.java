package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.dept.Dept;
import com.example.admin.domain.model.role.DataScope;
import com.example.admin.domain.model.role.DataScopeFilter;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 数据权限策略测试。
 *
 * <p>重点覆盖 {@code DEPT_AND_CHILD} 的部门树递归：这条路径原先是"降级为本部门"，
 * 现在真的会向下展开，判错就会变成越权（多给了别的部门的数据）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@ExtendWith(MockitoExtension.class)
class DataScopePolicyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private DeptRepository deptRepository;

    @InjectMocks
    private DataScopePolicy policy;

    @Test
    void deptAndChildCoversAllDescendants() {
        // 部门树：1 → 2 → 3；9 是另一棵不相关的树
        when(deptRepository.findAll(any(), any())).thenReturn(
                List.of(dept(1L, null), dept(2L, 1L), dept(3L, 2L), dept(9L, null)));
        mockRoles(DataScope.DEPT_AND_CHILD);

        DataScopeFilter filter = policy.filterFor(user(10L, 1L));

        assertFalse(filter.isUnrestricted());
        assertEquals(Set.of(1L, 2L, 3L), filter.getDeptIds());
    }

    @Test
    void deptAndChildAccessJudgementFollowsSubTree() {
        when(deptRepository.findAll(any(), any())).thenReturn(
                List.of(dept(1L, null), dept(2L, 1L), dept(3L, 2L), dept(9L, null)));
        mockRoles(DataScope.DEPT_AND_CHILD);
        LoginUser operator = user(10L, 1L);

        // 子部门里的人看得到
        assertTrue(policy.canAccessUser(operator, 55L, 3L));
        // 本人所在部门看得到
        assertTrue(policy.canAccessUser(operator, 56L, 1L));
        // 树外的部门看不到
        assertFalse(policy.canAccessUser(operator, 57L, 9L));
        // 目标没有部门信息时按"看不到"处理（fail-close）
        assertFalse(policy.canAccessUser(operator, 58L, null));
    }

    @Test
    void deptScopeCoversOnlyOwnDept() {
        mockRoles(DataScope.DEPT);

        DataScopeFilter filter = policy.filterFor(user(10L, 1L));

        assertEquals(Set.of(1L), filter.getDeptIds());
        assertFalse(policy.canAccessUser(user(10L, 1L), 55L, 2L));
    }

    @Test
    void customScopeStillDegradesToOwnDept() {
        // 现状记录：CUSTOM 需要 sys_role_dept 表才能落地，在此之前仍按本部门处理。
        // 一旦接入真实实现，这个断言应当随之更新。
        mockRoles(DataScope.CUSTOM);

        assertEquals(Set.of(1L), policy.filterFor(user(10L, 1L)).getDeptIds());
    }

    @Test
    void superAdminIsUnrestricted() {
        LoginUser admin = user(1L, 1L);

        assertTrue(admin.isSuperAdmin());
        assertTrue(policy.filterFor(admin).isUnrestricted());
        // 超管不受任何范围限制：运维排查跨部门问题是刚需
        assertTrue(policy.canAccessUser(admin, 99L, 42L));
    }

    @Test
    void noOperatorMeansNoAccess() {
        assertFalse(policy.canAccessUser(null, 55L, 1L));
    }

    private void mockRoles(DataScope scope) {
        when(userRepository.findRoleIdsByUserId(10L)).thenReturn(List.of(100L));
        when(roleRepository.findByIds(anyList())).thenReturn(List.of(role(100L, scope)));
    }

    private static LoginUser user(Long userId, Long deptId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setDeptId(deptId);
        return user;
    }

    private static Dept dept(Long id, Long parentId) {
        Dept dept = new Dept();
        dept.setId(id);
        dept.setParentId(parentId);
        return dept;
    }

    private static Role role(Long id, DataScope scope) {
        Role role = new Role();
        role.setId(id);
        role.setDataScope(scope.code());
        return role;
    }
}
