package com.example.admin.domain.repository;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.model.user.UserQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>为什么由领域层定义接口：应用服务只依赖抽象，
 * 底层从 MyBatis-Plus 换成 JPA 或远程服务时，领域层与应用层代码零改动（依赖倒置）。
 *
 * <p>仓储以"聚合"为单位存取：{@link #save(User)} 会同时持久化用户的角色关联，
 * 保证聚合内数据一致性由仓储实现负责，应用服务无需关心关联表细节。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface UserRepository {

    /**
     * 按 ID 查询用户（含角色关联）。
     *
     * @param id 用户 ID
     * @return 用户聚合；不存在返回 empty
     */
    Optional<User> findById(Long id);

    /**
     * 按用户名查询用户（登录场景）。
     *
     * @param username 用户名
     * @return 用户聚合；不存在返回 empty
     */
    Optional<User> findByUsername(String username);

    /**
     * 分页条件查询。
     *
     * @param query 查询条件（含分页参数）
     * @return 分页结果
     */
    PageResult<User> search(UserQuery query);

    /**
     * 保存用户聚合（新增或更新，含角色关联同步）。
     *
     * @param user 用户聚合根
     * @return 保存后的聚合（含生成的主键）
     */
    User save(User user);

    /**
     * 逻辑删除（批量）。
     *
     * @param ids 用户 ID 集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 判断用户名是否已存在。
     *
     * @param username  用户名
     * @param excludeId 排除的用户 ID（修改时传自身 ID），新增传 null
     * @return 是否已存在
     */
    boolean existsByUsername(String username, Long excludeId);

    /**
     * 批量查询用户所属部门 ID。
     *
     * <p>存在理由：同 {@code DeptRepository#findNamesByIds}，
     * 避免在列表接口里对每一行逐条查库。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID → 部门 ID（部门为空时值为 null）
     */
    Map<Long, Long> findDeptIdMapByUserIds(List<Long> userIds);

    /**
     * 判断手机号是否已存在。
     *
     * @param phone     手机号
     * @param excludeId 排除的用户 ID
     * @return 是否已存在
     */
    boolean existsByPhone(String phone, Long excludeId);

    /**
     * 统计用户总数。
     *
     * @return 总数
     */
    long count();

    /**
     * 查询指定用户拥有的角色 ID 集合。
     *
     * <p>用于数据权限切面：定位"当前用户能用什么数据范围"。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> findRoleIdsByUserId(Long userId);

    /**
     * 批量加载用户的角色列表（避免 N+1 查询）。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID → 角色列表
     */
    Map<Long, List<Role>> findRolesByUserIds(List<Long> userIds);
}
