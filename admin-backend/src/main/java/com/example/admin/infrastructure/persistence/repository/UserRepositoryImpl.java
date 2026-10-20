package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.model.user.UserQuery;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.model.role.DataScopeFilter;
import com.example.admin.infrastructure.persistence.converter.RoleConverter;
import com.example.admin.infrastructure.security.DataScopeContext;
import com.example.admin.infrastructure.persistence.converter.UserConverter;
import com.example.admin.infrastructure.persistence.mapper.SysRoleMapper;
import com.example.admin.infrastructure.persistence.mapper.SysUserMapper;
import com.example.admin.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.example.admin.infrastructure.persistence.po.SysRole;
import com.example.admin.infrastructure.persistence.po.SysUser;
import com.example.admin.infrastructure.persistence.po.SysUserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户仓储实现（Infrastructure 层）：把领域仓储接口翻译为 MyBatis-Plus 调用。
 *
 * <p>分层职责：
 * <ul>
 *     <li>ORM 细节（LambdaQueryWrapper、分页插件、逻辑删除）只出现在这一层；</li>
 *     <li>以"聚合"为单位读写：保存用户时同步用户-角色关联，保证聚合一致性；</li>
 *     <li>所有查询均为参数化查询（{@code like}/{@code eq} 由 MP 预编译），禁止拼接 SQL。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final SysUserMapper userMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final SysRoleMapper roleMapper;

    private final UserConverter userConverter;

    private final RoleConverter roleConverter;

    /**
     * 按 ID 查询用户（含角色关联）。
     *
     * @param id 用户 ID
     * @return 用户聚合
     */
    @Override
    public Optional<User> findById(Long id) {
        SysUser po = userMapper.selectById(id);
        return Optional.ofNullable(toDomainWithRoles(po));
    }

    /**
     * 按用户名查询用户。
     *
     * @param username 用户名
     * @return 用户聚合
     */
    @Override
    public Optional<User> findByUsername(String username) {
        SysUser po = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        return Optional.ofNullable(toDomainWithRoles(po));
    }

    /**
     * 分页条件查询。
     *
     * @param query 查询条件
     * @return 分页结果（领域模型）
     */
    @Override
    public Map<Long, Long> findDeptIdMapByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds)
                .select(SysUser::getId, SysUser::getDeptId));
        Map<Long, Long> map = new HashMap<>();
        for (SysUser user : users) {
            if (user.getId() != null) {
                map.put(user.getId(), user.getDeptId());
            }
        }
        return map;
    }

    @Override
    public PageResult<User> search(UserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                // 关键字同时匹配用户名与昵称；like 由 MP 参数化处理，禁止手写 % 拼接
                .and(StrUtil.isNotBlank(query.getKeyword()),
                        w -> w.like(SysUser::getUsername, query.getKeyword())
                                .or()
                                .like(SysUser::getNickname, query.getKeyword()))
                .like(StrUtil.isNotBlank(query.getPhone()), SysUser::getPhone, query.getPhone())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .ge(query.getBeginTime() != null, SysUser::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysUser::getCreateTime, query.getEndTime())
                .orderByDesc(SysUser::getCreateTime);

        // 数据权限过滤：读线程上下文，按 DataScopeFilter 应用 dept_id 或 user_id 条件
        // 调用方（@DataScopeAnno 标注的 Controller/Service）会保证上下文被正确设置与清理
        DataScopeFilter dataFilter = DataScopeContext.get();
        if (dataFilter == null) {
            // fail-close：跨用户查询必须在数据权限上下文内执行。
            // 若这里放行，"某个查询忘了加 @DataScopeAnno"就会变成一次全量数据泄露，
            // 而这种遗漏在代码评审里极难被发现；宁可查不到，也不能默认全给。
            log.warn("数据权限上下文缺失，拒绝返回用户列表（请确认调用方已标注 @DataScopeAnno）");
            wrapper.eq(SysUser::getId, -1L);
        } else if (!dataFilter.isUnrestricted()) {
            if (dataFilter.getSelfUserId() != null) {
                // SELF 范围：仅本人
                wrapper.eq(SysUser::getId, dataFilter.getSelfUserId());
            } else if (dataFilter.getDeptIds() != null && !dataFilter.getDeptIds().isEmpty()) {
                // DEPT / DEPT_AND_CHILD / CUSTOM 范围：按部门集合过滤
                wrapper.in(SysUser::getDeptId, dataFilter.getDeptIds());
            }
            // 部门集合为空（异常边界）→ 查不到任何数据，避免"无限制暴露"
        }

        IPage<SysUser> page = userMapper.selectPage(query.buildPage(), wrapper);

        List<Long> userIds = page.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
        Map<Long, List<Long>> roleIdMap = findRoleIdsByUserIds(userIds);

        List<User> records = new ArrayList<>();
        for (SysUser po : page.getRecords()) {
            User user = userConverter.toDomain(po);
            user.assignRoles(roleIdMap.getOrDefault(po.getId(), Collections.emptyList()));
            records.add(user);
        }
        return toDomainPage(page, records);
    }

    /**
     * 保存用户聚合（新增或更新，含角色关联同步）。
     *
     * @param user 用户聚合根
     * @return 保存后的聚合
     */
    @Override
    public User save(User user) {
        SysUser po = userConverter.toPo(user);
        if (user.getId() == null) {
            userMapper.insert(po);
            user.setId(po.getId());
        } else {
            userMapper.updateById(po);
        }
        saveRoleRelations(user.getId(), user.getRoleIds());
        return user;
    }

    /**
     * 逻辑删除（批量），同时清理用户-角色关联。
     *
     * @param ids 用户 ID 集合
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 一条 DELETE ... WHERE user_id IN (...)：循环里逐 id 删除会让"批量删除 100 个用户"发出 100 条 DELETE
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, ids));
        // MP 在配置了逻辑删除字段后，deleteBatchIds 会转换为 UPDATE ... SET deleted = 1
        userMapper.deleteBatchIds(ids);
    }

    /**
     * 判断用户名是否已存在。
     *
     * @param username  用户名
     * @param excludeId 排除的用户 ID
     * @return 是否已存在
     */
    @Override
    public boolean existsByUsername(String username, Long excludeId) {
        if (StrUtil.isBlank(username)) {
            return false;
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        return count != null && count > 0;
    }

    /**
     * 判断手机号是否已存在。
     *
     * @param phone     手机号
     * @param excludeId 排除的用户 ID
     * @return 是否已存在
     */
    @Override
    public boolean existsByPhone(String phone, Long excludeId) {
        if (StrUtil.isBlank(phone)) {
            return false;
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(excludeId != null, SysUser::getId, excludeId));
        return count != null && count > 0;
    }

    /**
     * 统计用户总数。
     *
     * @return 总数
     */
    @Override
    public long count() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>());
        return count == null ? 0L : count;
    }

    /**
     * 批量加载用户的角色列表（避免 N+1 查询）。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID → 角色列表
     */
    @Override
    public Map<Long, List<Role>> findRolesByUserIds(List<Long> userIds) {
        Map<Long, List<Role>> result = new HashMap<>();
        if (CollUtil.isEmpty(userIds)) {
            return result;
        }
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds));
        if (CollUtil.isEmpty(relations)) {
            return result;
        }
        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).distinct().collect(Collectors.toList());
        Map<Long, Role> roleMap = roleMapper.selectBatchIds(roleIds).stream()
                .map(roleConverter::toDomain)
                .collect(Collectors.toMap(Role::getId, role -> role, (a, b) -> a));
        for (SysUserRole relation : relations) {
            Role role = roleMap.get(relation.getRoleId());
            if (role != null) {
                result.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(role);
            }
        }
        return result;
    }

    /**
     * PO 转领域模型并填充角色 ID。
     *
     * @param po 持久化对象
     * @return 用户聚合根
     */
    private User toDomainWithRoles(SysUser po) {
        if (po == null) {
            return null;
        }
        User user = userConverter.toDomain(po);
        user.assignRoles(findRoleIdsByUserId(po.getId()));
        return user;
    }

    /**
     * 查询单个用户的角色 ID 集合。
     *
     * @param userId 用户 ID
     * @return 角色 ID 集合
     */
    @Override
    public List<Long> findRoleIdsByUserId(Long userId) {
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        return relations.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    /**
     * 批量查询用户的角色 ID 集合。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID → 角色 ID 列表
     */
    private Map<Long, List<Long>> findRoleIdsByUserIds(List<Long> userIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (CollUtil.isEmpty(userIds)) {
            return result;
        }
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds));
        for (SysUserRole relation : relations) {
            result.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleId());
        }
        return result;
    }

    /**
     * 保存用户-角色关联（先删后插，保证与聚合状态一致）。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 集合
     */
    private void saveRoleRelations(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (CollUtil.isEmpty(roleIds)) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    /**
     * 将 MyBatis-Plus 分页结果转换为领域分页结果。
     *
     * @param page    MP 分页对象
     * @param records 已转换的领域记录
     * @return 统一分页响应
     */
    private PageResult<User> toDomainPage(IPage<SysUser> page, List<User> records) {
        PageResult<User> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
