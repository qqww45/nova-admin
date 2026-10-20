package com.example.admin.application.assembler;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.interfaces.dto.resp.ProfileResp;
import com.example.admin.interfaces.dto.resp.RoleOptionResp;
import com.example.admin.common.util.SensitiveUtils;
import com.example.admin.interfaces.dto.resp.UserResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户领域模型 → 响应 DTO 装配器。
 *
 * <p>职责边界：
 * <ul>
 *     <li><b>脱敏在这里做</b>：领域模型保存完整数据（个人中心需要完整手机号），
 *         对外响应时才做脱敏，保证"安全是出口策略"而不是污染领域模型；</li>
 *     <li>ID 统一转 String：避免前端 JS 大整数精度丢失（雪花 ID 超过 2^53）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class UserAssembler {

    private final DeptRepository deptRepository;

    /**
     * 领域模型转列表响应（手机号/邮箱脱敏，含部门名）。
     *
     * @param user  用户聚合
     * @param roles 用户拥有的角色列表（可为空集合）
     * @return 用户响应 DTO（脱敏）
     */
    public UserResp toResp(User user, List<Role> roles) {
        return toResp(user, roles, true);
    }

    /**
     * 领域模型转"可编辑"详情响应（手机号/邮箱不脱敏）。
     *
     * <p>为什么这条路径不脱敏：编辑弹窗会把详情值原样回填再提交，
     * 带掩码的值过不了 {@code UserSaveReq} 的格式校验（曾因此报"手机号格式不正确"）。
     * 暴露面评估：进编辑弹窗需要 {@code system:user:edit}，该能力本就包含
     * 重置密码、停用账号这类更强操作，手机号明文不构成额外的风险边界；
     * 列表接口仍保持脱敏，避免批量暴露。
     *
     * @param user  用户聚合
     * @param roles 用户拥有的角色列表
     * @return 未脱敏的用户响应 DTO
     */
    public UserResp toEditableResp(User user, List<Role> roles) {
        return toResp(user, roles, false);
    }

    private UserResp toResp(User user, List<Role> roles, boolean mask) {
        UserResp resp = new UserResp();
        resp.setId(String.valueOf(user.getId()));
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setEmail(mask ? SensitiveUtils.email(user.getEmail()) : user.getEmail());
        resp.setPhone(mask ? SensitiveUtils.phone(user.getPhone()) : user.getPhone());
        resp.setSex(user.getSex());
        resp.setAvatar(user.getAvatar());
        resp.setStatus(user.getStatus());
        resp.setTheme(user.getTheme());
        resp.setDeptId(String.valueOf(user.getDeptId()));
        resp.setDeptName(deptRepository.findNameById(user.getDeptId()));
        resp.setLoginIp(user.getLoginIp());
        resp.setLoginDate(user.getLoginDate());
        resp.setRemark(user.getRemark());
        resp.setCreateTime(user.getCreateTime());
        List<Role> roleList = roles == null ? Collections.emptyList() : roles;
        resp.setRoleIds(roleList.stream().map(role -> String.valueOf(role.getId())).collect(Collectors.toList()));
        resp.setRoleNames(roleList.stream().map(Role::getRoleName).collect(Collectors.toList()));
        return resp;
    }

    /**
     * 分页结果转换（保持分页元信息，逐条装配）。
     *
     * @param page     领域分页结果
     * @param rolesMap 用户 ID → 角色列表
     * @return DTO 分页结果
     */
    public PageResult<UserResp> toPage(PageResult<User> page, Map<Long, List<Role>> rolesMap) {
        List<UserResp> records = new ArrayList<>();
        for (User user : page.getRecords()) {
            records.add(toResp(user, rolesMap.getOrDefault(user.getId(), Collections.emptyList())));
        }
        PageResult<UserResp> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        return result;
    }

    /**
     * 领域模型转个人中心响应（个人资料不脱敏，因为是本人查看自己的数据）。
     *
     * @param user  用户聚合
     * @param roles 角色列表
     * @return 个人中心响应 DTO
     */
    public ProfileResp toProfile(User user, List<Role> roles) {
        ProfileResp resp = new ProfileResp();
        resp.setId(String.valueOf(user.getId()));
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setSex(user.getSex());
        resp.setAvatar(user.getAvatar());
        resp.setDeptName(deptRepository.findNameById(user.getDeptId()));
        resp.setRoleNames(roles == null ? Collections.emptyList()
                : roles.stream().map(Role::getRoleName).collect(Collectors.toList()));
        resp.setTheme(user.getTheme());
        resp.setLoginIp(user.getLoginIp());
        resp.setLoginDate(user.getLoginDate());
        resp.setCreateTime(user.getCreateTime());
        resp.setRemark(user.getRemark());
        return resp;
    }

    /**
     * 角色领域模型转下拉选项。
     *
     * @param roles 角色列表
     * @return 下拉选项列表
     */
    public List<RoleOptionResp> toRoleOptions(List<Role> roles) {
        if (roles == null) {
            return new ArrayList<>();
        }
        return roles.stream().map(role -> RoleOptionResp.builder()
                .id(String.valueOf(role.getId()))
                .roleName(role.getRoleName())
                .roleKey(role.getRoleKey())
                .status(role.getStatus())
                .build()).collect(Collectors.toList());
    }
}
