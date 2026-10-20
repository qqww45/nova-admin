package com.example.admin.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.application.assembler.UserAssembler;
import com.example.admin.application.service.UserAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.model.user.UserQuery;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.service.DataScopePolicy;
import com.example.admin.domain.service.PasswordHasher;
import com.example.admin.domain.port.LoginUserCache;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.interfaces.dto.req.ChangePasswordReq;
import com.example.admin.interfaces.dto.req.ProfileUpdateReq;
import com.example.admin.interfaces.dto.req.ResetPasswordReq;
import com.example.admin.interfaces.dto.req.UpdateStatusReq;
import com.example.admin.interfaces.dto.req.UserPageQuery;
import com.example.admin.interfaces.dto.req.UserSaveReq;
import com.example.admin.interfaces.dto.resp.ProfileResp;
import com.example.admin.interfaces.dto.resp.RoleOptionResp;
import com.example.admin.interfaces.dto.resp.UserResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户应用服务实现（用例编排）。
 *
 * <p>执行顺序约定：加载聚合 → 调用聚合内的业务行为（内含规则校验）→ 通过仓储持久化。
 * 所有跨聚合的校验（如用户名唯一性）由应用层通过仓储完成，
 * 单一聚合内的不变量（如超级管理员保护）由聚合根自身守护。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAppServiceImpl implements UserAppService {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordHasher passwordHasher;

    private final UserAssembler userAssembler;

    private final OperatorContext operatorContext;

    private final LoginUserCache loginUserCache;

    private final DataScopePolicy dataScopePolicy;

    /**
     * 分页查询用户。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<UserResp> pageUsers(UserPageQuery query) {
        // DTO → 领域查询对象（应用层负责协议转换）
        UserQuery domainQuery = new UserQuery();
        domainQuery.setPageNum(query.getPageNum());
        domainQuery.setPageSize(query.getPageSize());
        domainQuery.setKeyword(query.getKeyword());
        domainQuery.setPhone(query.getPhone());
        domainQuery.setStatus(query.getStatus());
        domainQuery.setBeginTime(query.getBeginTime());
        domainQuery.setEndTime(query.getEndTime());
        if (StrUtil.isNotBlank(query.getDeptId())) {
            domainQuery.setDeptId(IdParser.parseId(query.getDeptId()));
        }

        PageResult<User> page = userRepository.search(domainQuery);
        List<Long> userIds = page.getRecords().stream().map(User::getId).toList();
        Map<Long, List<Role>> rolesMap = userRepository.findRolesByUserIds(userIds);
        return userAssembler.toPage(page, rolesMap);
    }

    /**
     * 查询用户详情。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    @Override
    public UserResp getUserDetail(String id) {
        User user = requireUser(IdParser.parseId(id));
        assertUserInDataScope(user);
        List<Role> roles = userRepository.findRolesByUserIds(List.of(user.getId()))
                .getOrDefault(user.getId(), Collections.emptyList());
        // 编辑弹窗会把详情值原样回填再提交，必须返回明文，
        // 否则带掩码的手机号/邮箱过不了保存时的格式校验（列表接口仍脱敏）
        return userAssembler.toEditableResp(user, roles);
    }

    /**
     * 新增用户。
     *
     * @param req 用户参数
     * @return 新用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createUser(UserSaveReq req) {
        if (StrUtil.isBlank(req.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新增用户时密码不能为空");
        }
        assertNotMasked(req.getPhone(), req.getEmail());
        checkUnique(req.getUsername(), req.getPhone(), null);

        User user = new User();
        fillBasicFields(user, req);
        user.setPasswordHash(passwordHasher.encode(req.getPassword()));
        List<Long> newRoleIds = IdParser.parseIds(req.getRoleIds());
        assertRolesAssignable(null, newRoleIds);
        user.assignRoles(newRoleIds);
        userRepository.save(user);
        log.info("新增用户成功 username={} id={}", user.getUsername(), user.getId());
        return String.valueOf(user.getId());
    }

    /**
     * 修改用户。
     *
     * @param req 用户参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserSaveReq req) {
        Long userId = IdParser.parseId(req.getId());
        User user = requireUser(userId);
        assertTargetOperable(userId);
        assertUserInDataScope(user);
        assertNotMasked(req.getPhone(), req.getEmail());
        checkUnique(req.getUsername(), req.getPhone(), userId);
        // 用户名可能被改：新旧两个 key 都要失效，否则改名后旧 key 的缓存还会命中
        String oldUsername = user.getUsername();

        fillBasicFields(user, req);
        // 密码留空表示不修改
        if (StrUtil.isNotBlank(req.getPassword())) {
            user.setPasswordHash(passwordHasher.encode(req.getPassword()));
        }
        // 未提交角色集合时保持原有角色，避免"只改资料"误清空角色
        if (req.getRoleIds() != null) {
            List<Long> newRoleIds = IdParser.parseIds(req.getRoleIds());
            assertRolesAssignable(userId, newRoleIds);
            user.assignRoles(newRoleIds);
        }
        userRepository.save(user);
        // 资料与角色变化会改变这个人"能做什么"，必须立刻失效，
        // 否则他会在缓存有效期内继续持有已经不属于他的权限。
        loginUserCache.evict(oldUsername);
        loginUserCache.evict(user.getUsername());
    }

    /**
     * 批量删除用户。
     *
     * @param ids 用户 ID 集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUsers(List<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        List<Long> userIds = new ArrayList<>();
        for (String id : ids) {
            userIds.add(IdParser.parseId(id));
        }
        // 领域规则之外的数据完整性保护（涉及"当前登录者"这一会话上下文，属于应用层职责）
        if (userIds.contains(Constants.SUPER_ADMIN_USER_ID)) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "超级管理员账号不可删除");
        }
        Long currentUserId = operatorContext.currentUserIdOrNull();
        if (currentUserId != null && userIds.contains(currentUserId)) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "不能删除当前登录的用户");
        }
        // 批量删除同样要过数据范围：列表里看不到的人，不该因为"传了 ID"就能删掉
        Map<Long, Long> deptMap = userRepository.findDeptIdMapByUserIds(userIds);
        LoginUser operator = operatorContext.current();
        for (Long targetId : userIds) {
            if (!dataScopePolicy.canAccessUser(operator, targetId, deptMap.get(targetId))) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权删除部分用户（超出数据范围）");
            }
        }
        userRepository.deleteByIds(userIds);
        loginUserCache.clear();
    }

    /**
     * 重置用户密码（管理员操作）。
     *
     * @param req 重置密码参数
     */
    @Override
    public void resetPassword(ResetPasswordReq req) {
        User user = requireUser(IdParser.parseId(req.getUserId()));
        // 改角色不是唯一的提权路径：重置密码同样能把别人的账号据为己有。
        // 少了这道校验，持有 system:user:resetPwd 的普通管理员只要传 id=1 就能改掉超管密码，
        // 前面 assertRolesAssignable 那道防线被完全绕开。
        assertTargetOperable(user.getId());
        assertUserInDataScope(user);
        // 重置密码是管理员行为，无需原密码：直接调用聚合行为
        user.resetPassword(passwordHasher, req.getPassword());
        userRepository.save(user);
        loginUserCache.evict(user.getUsername());
    }

    /**
     * 修改用户状态（超级管理员保护由聚合根守护）。
     *
     * @param req 状态参数
     */
    @Override
    public void updateStatus(UpdateStatusReq req) {
        User user = requireUser(IdParser.parseId(req.getId()));
        assertTargetOperable(user.getId());
        assertUserInDataScope(user);
        try {
            user.changeStatus(req.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "超级管理员账号不可停用");
        }
        userRepository.save(user);
        // 停用必须立即生效：否则账号在缓存有效期内还能继续调用接口，"停用"就是假的
        loginUserCache.evict(user.getUsername());
    }

    /**
     * 获取个人中心信息。
     *
     * @param userId 用户 ID
     * @return 个人资料
     */
    @Override
    public ProfileResp getProfile(Long userId) {
        User user = requireUser(userId);
        List<Role> roles = userRepository.findRolesByUserIds(List.of(userId))
                .getOrDefault(userId, Collections.emptyList());
        return userAssembler.toProfile(user, roles);
    }

    /**
     * 修改个人资料。
     *
     * @param userId 用户 ID
     * @param req    资料参数
     */
    @Override
    public void updateProfile(Long userId, ProfileUpdateReq req) {
        User user = requireUser(userId);
        user.updateProfile(req.getNickname(), req.getEmail(), req.getPhone(), req.getSex(), req.getAvatar());
        userRepository.save(user);
    }

    /**
     * 修改密码（两次输入一致性属应用层校验，密码规则由聚合守护）。
     *
     * @param userId 用户 ID
     * @param req    密码参数
     */
    @Override
    public void changePassword(Long userId, ChangePasswordReq req) {
        if (!Objects.equals(req.getNewPassword(), req.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "两次输入的新密码不一致");
        }
        User user = requireUser(userId);
        try {
            user.changePassword(passwordHasher, req.getOldPassword(), req.getNewPassword());
        } catch (IllegalArgumentException e) {
            if ("SAME_PASSWORD".equals(e.getMessage())) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "新密码不能与原密码相同");
            }
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        userRepository.save(user);
    }

    /**
     * 更新主题偏好。
     *
     * @param userId 用户 ID
     * @param theme  主题
     */
    @Override
    public void updateTheme(Long userId, String theme) {
        User user = requireUser(userId);
        user.updateTheme(theme);
        userRepository.save(user);
    }

    /**
     * 更新最后登录信息。
     *
     * @param userId 用户 ID
     * @param ip     登录 IP
     */
    @Override
    public void updateLoginInfo(Long userId, String ip) {
        User user = requireUser(userId);
        user.recordLogin(ip, LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 查询可用角色选项。
     *
     * @return 角色选项列表
     */
    @Override
    public List<RoleOptionResp> listRoleOptions() {
        List<Role> all = roleRepository.findAll();
        List<Role> enabled = all.stream().filter(Role::isEnabled).toList();
        return userAssembler.toRoleOptions(enabled);
    }

    /**
     * 按 ID 加载用户，不存在抛出业务异常。
     *
     * @param id 用户 ID
     * @return 用户聚合
     */
    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND));
    }

    /**
     * 拒绝把脱敏值当真实数据写库。
     *
     * <p>前端某些路径（旧标签页、缓存的列表数据）可能把掩码值原样提交：
     * 手机号有严格格式校验能拦住，但邮箱的 {@code @Email} 校验较宽松，
     * "2***@qq.com" 这类值能通过——admin 的邮箱就是这样被写脏的。
     * 与其赌前端永远不提交掩码值，不如在入口明确拒绝。
     *
     * @param phone 手机号（可空）
     * @param email 邮箱（可空）
     */
    private void assertNotMasked(String phone, String email) {
        if (phone != null && phone.contains("***")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号不能包含掩码字符，请填写完整手机号");
        }
        if (email != null && email.contains("***")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱不能包含掩码字符，请填写完整邮箱");
        }
    }

    /**
     * 校验用户名与手机号唯一性。
     *
     * @param username 用户名
     * @param phone    手机号（可空）
     * @param excludeId 排除的用户 ID（新增传 null）
     */
    private void checkUnique(String username, String phone, Long excludeId) {
        if (userRepository.existsByUsername(username, excludeId)) {
            throw new BusinessException(ResultCode.DATA_EXIST, "用户名已存在");
        }
        if (StrUtil.isNotBlank(phone) && userRepository.existsByPhone(phone, excludeId)) {
            throw new BusinessException(ResultCode.DATA_EXIST, "手机号已被使用");
        }
    }

    /**
     * 填充用户基础字段（新增与修改共用）。
     *
     * @param user 用户聚合
     * @param req  请求参数
     */
    private void fillBasicFields(User user, UserSaveReq req) {
        user.setUsername(req.getUsername());
        user.setNickname(req.getNickname());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setSex(req.getSex() == null ? 0 : req.getSex());
        user.setAvatar(req.getAvatar());
        user.setStatus(req.getStatus() == null ? Constants.STATUS_NORMAL : req.getStatus());
        user.setDeptId(StrUtil.isBlank(req.getDeptId()) ? Constants.TOP_PARENT_ID : IdParser.parseId(req.getDeptId()));
        user.setRemark(req.getRemark());
    }

    /**
     * 校验"操作者是否有权授予这些角色"（防垂直越权）。
     *
     * <p>为什么必须有这道校验：{@code sys_user_role} 是"先全删再全插"，
     * 应用层若来者不拒，那么持有 {@code system:user:edit} 的普通管理员
     * 只需在改用户时带上超管角色 ID，下一步请求就会以 {@code admin} 角色
     * 重新加载权限（{@code *:*:*} 通配）→ 一步成为超级管理员。
     * 接口级权限注解拦不住这种"用合法接口完成提权"的调用。
     *
     * @param targetUserId 被修改的用户 ID（新增场景为 null）
     * @param roleIds      待授予的角色 ID
     * @throws BusinessException 越权时抛出
     */
    /**
     * 校验目标用户是否在操作者的数据可见范围内（防水平越权）。
     *
     * <p>为什么列表之外还要单独判一次：数据范围原先只在列表查询的 SQL 上生效，
     * 于是"只能看本部门"的管理员虽然列表里看不到别的部门的人，
     * 但只要直接传对方的 ID，详情、修改、停用照样能成 —— 列表过滤只是障眼法。
     * 凡是"按 ID 操作某个用户"的入口，都必须过这一道。
     *
     * @param target 目标用户
     * @throws BusinessException 超出数据范围时抛出
     */
    private void assertUserInDataScope(User target) {
        if (!dataScopePolicy.canAccessUser(operatorContext.current(), target.getId(), target.getDeptId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该用户（超出数据范围）");
        }
    }

    /**
     * 校验操作者是否有权改动指定账号（防垂直越权）。
     *
     * <p>为什么需要它：{@link #assertRolesAssignable} 只堵住了"给自己挂超管角色"这一条路，
     * 但提权不止一条路 —— 重置超管密码、停用超管账号、改写超管资料，效果完全一样，
     * 而且用的都是各自合法的接口权限。所以每个"按 ID 操作账号"的入口都要过这一道。
     *
     * @param targetUserId 被操作的账号 ID
     * @throws BusinessException 非超级管理员操作内置管理员账号时抛出
     */
    private void assertTargetOperable(Long targetUserId) {
        LoginUser operator = operatorContext.current();
        if (operator != null && operator.isSuperAdmin()) {
            return;
        }
        if (Constants.SUPER_ADMIN_USER_ID.equals(targetUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有超级管理员可以操作内置管理员账号");
        }
    }

    private void assertRolesAssignable(Long targetUserId, List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return;
        }
        LoginUser operator = operatorContext.current();
        if (operator != null && operator.isSuperAdmin()) {
            // 超级管理员不受限制
            return;
        }
        if (targetUserId != null && Constants.SUPER_ADMIN_USER_ID.equals(targetUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有超级管理员可以修改内置管理员账号的角色");
        }
        List<Role> targets = roleRepository.findByIds(roleIds);
        if (targets == null) {
            return;
        }
        for (Role role : targets) {
            if (Constants.SUPER_ADMIN_ROLE_KEY.equals(role.getRoleKey())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权授予超级管理员角色");
            }
        }
    }

}
