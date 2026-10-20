package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.ChangePasswordReq;
import com.example.admin.interfaces.dto.req.ProfileUpdateReq;
import com.example.admin.interfaces.dto.req.ResetPasswordReq;
import com.example.admin.interfaces.dto.req.UpdateStatusReq;
import com.example.admin.interfaces.dto.req.UserPageQuery;
import com.example.admin.interfaces.dto.req.UserSaveReq;
import com.example.admin.interfaces.dto.resp.ProfileResp;
import com.example.admin.interfaces.dto.resp.RoleOptionResp;
import com.example.admin.interfaces.dto.resp.UserResp;

import java.util.List;

/**
 * 用户应用服务（用例编排层）。
 *
 * <p>应用层职责边界：
 * <ul>
 *     <li>负责事务边界、参数转换（DTO → 领域查询对象）、领域对象编排与仓储调用；</li>
 *     <li><b>不写业务规则</b>：密码规则、启停保护等在 {@code User} 聚合根内；</li>
 *     <li><b>不写 ORM 代码</b>：所有持久化经 {@code UserRepository}。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface UserAppService {

    /**
     * 分页查询用户。
     *
     * @param query 查询条件
     * @return 分页结果（手机号/邮箱已脱敏）
     */
    PageResult<UserResp> pageUsers(UserPageQuery query);

    /**
     * 查询用户详情（含角色 ID 集合）。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    UserResp getUserDetail(String id);

    /**
     * 新增用户（含角色分配）。
     *
     * @param req 用户参数
     * @return 新用户 ID
     */
    String createUser(UserSaveReq req);

    /**
     * 修改用户（含角色分配）。
     *
     * @param req 用户参数
     */
    void updateUser(UserSaveReq req);

    /**
     * 批量删除用户（逻辑删除）。
     *
     * @param ids 用户 ID 集合
     */
    void removeUsers(List<String> ids);

    /**
     * 重置用户密码（管理员操作）。
     *
     * @param req 重置密码参数
     */
    void resetPassword(ResetPasswordReq req);

    /**
     * 修改用户状态。
     *
     * @param req 状态参数
     */
    void updateStatus(UpdateStatusReq req);

    /**
     * 获取个人中心信息。
     *
     * @param userId 用户 ID
     * @return 个人资料
     */
    ProfileResp getProfile(Long userId);

    /**
     * 修改个人资料。
     *
     * @param userId 用户 ID
     * @param req    资料参数
     */
    void updateProfile(Long userId, ProfileUpdateReq req);

    /**
     * 修改密码（需校验原密码）。
     *
     * @param userId 用户 ID
     * @param req    密码参数
     */
    void changePassword(Long userId, ChangePasswordReq req);

    /**
     * 更新主题偏好（跨设备同步）。
     *
     * @param userId 用户 ID
     * @param theme  主题：light / dark / auto
     */
    void updateTheme(Long userId, String theme);

    /**
     * 更新最后登录信息。
     *
     * @param userId 用户 ID
     * @param ip     登录 IP
     */
    void updateLoginInfo(Long userId, String ip);

    /**
     * 查询所有可用角色（分配角色时的下拉选项）。
     *
     * @return 角色选项列表
     */
    List<RoleOptionResp> listRoleOptions();
}
