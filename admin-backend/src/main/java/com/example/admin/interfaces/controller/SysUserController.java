package com.example.admin.interfaces.controller;

import cn.hutool.core.util.StrUtil;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.common.util.IdParser;
import com.example.admin.infrastructure.security.annotation.DataScopeAnno;
import com.example.admin.interfaces.dto.req.ResetPasswordReq;
import com.example.admin.interfaces.dto.req.UpdateStatusReq;
import com.example.admin.interfaces.dto.req.UserPageQuery;
import com.example.admin.interfaces.dto.req.UserSaveReq;
import com.example.admin.interfaces.dto.resp.RoleOptionResp;
import com.example.admin.interfaces.dto.resp.UserResp;
import com.example.admin.application.service.AuthAppService;
import com.example.admin.application.service.UserAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 用户管理控制器。
 *
 * <p>权限说明：每个接口都带有 {@code @PreAuthorize} 注解做后端校验，
 * 前端隐藏按钮只是体验优化，绝不作为安全边界。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/user")
@Tag(name = "用户管理", description = "用户增删改查、重置密码、启停状态")
public class SysUserController {

    private final UserAppService userService;

    private final AuthAppService authService;

    /**
     * 分页查询用户。
     *
     * @param query 查询条件（页码、条数、关键字等）
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @DataScopeAnno
    @Operation(summary = "分页查询用户")
    public R<PageResult<UserResp>> list(@Valid UserPageQuery query) {
        return R.ok(userService.pageUsers(query));
    }

    /**
     * 查询用户详情。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @Operation(summary = "查询用户详情")
    public R<UserResp> detail(@PathVariable String id) {
        return R.ok(userService.getUserDetail(id));
    }

    /**
     * 新增用户。
     *
     * @param req 用户参数
     * @return 新用户 ID
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:user:add')")
    @Operation(summary = "新增用户")
    public R<String> add(@Valid @RequestBody UserSaveReq req) {
        return R.ok(userService.createUser(req));
    }

    /**
     * 修改用户。
     *
     * @param req 用户参数
     * @return 空响应
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Operation(summary = "修改用户")
    public R<Void> edit(@Valid @RequestBody UserSaveReq req) {
        userService.updateUser(req);
        return R.ok();
    }

    /**
     * 批量删除用户（逻辑删除）。
     *
     * @param ids 用户 ID，多个用逗号分隔
     * @return 空响应
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('system:user:remove')")
    @Operation(summary = "批量删除用户", description = "逻辑删除；超级管理员不可删除")
    public R<Void> remove(@PathVariable String ids) {
        userService.removeUsers(splitIds(ids));
        return R.ok();
    }

    /**
     * 重置用户密码。
     *
     * @param req 重置密码参数
     * @return 空响应
     */
    @PutMapping("/resetPwd")
    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
    @Operation(summary = "重置用户密码")
    public R<Void> resetPwd(@Valid @RequestBody ResetPasswordReq req) {
        userService.resetPassword(req);
        return R.ok();
    }

    /**
     * 修改用户状态。
     *
     * @param req 状态参数
     * @return 空响应
     */
    @PutMapping("/status")
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Operation(summary = "修改用户状态")
    public R<Void> updateStatus(@Valid @RequestBody UpdateStatusReq req) {
        userService.updateStatus(req);
        return R.ok();
    }

    /**
     * 查询角色下拉选项（分配角色用）。
     *
     * @return 角色选项列表
     */
    @GetMapping("/roleOptions")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @Operation(summary = "查询角色下拉选项")
    public R<List<RoleOptionResp>> roleOptions() {
        return R.ok(userService.listRoleOptions());
    }

    /**
     * 强制用户下线（服务端主动踢人）。
     *
     * <p>实现：删除该用户所有设备的 refresh 令牌并撤销可信设备记录，
     * 使其所有设备在下一次刷新令牌时失败，必须重新登录并重新完成验证。
     *
     * @param id 用户 ID
     * @return 被下线的设备数量
     */
    @PostMapping("/{id}/kick")
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Operation(summary = "强制用户下线", description = "使该用户所有设备的登录态失效，需重新登录")
    public R<Integer> kick(@PathVariable String id) {
        return R.ok(authService.kick(IdParser.parseId(id)));
    }

    /**
     * 将逗号分隔的 ID 字符串拆分为列表。
     *
     * @param ids ID 字符串（如 1,2,3）
     * @return ID 列表
     */
    private List<String> splitIds(String ids) {
        if (StrUtil.isBlank(ids)) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
