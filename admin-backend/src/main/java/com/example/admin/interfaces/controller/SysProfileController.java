package com.example.admin.interfaces.controller;

import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.ChangePasswordReq;
import com.example.admin.interfaces.dto.req.ProfileUpdateReq;
import com.example.admin.interfaces.dto.req.ThemeUpdateReq;
import com.example.admin.interfaces.dto.resp.ProfileResp;
import com.example.admin.application.service.UserAppService;
import com.example.admin.infrastructure.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心控制器：资料查看/修改、修改密码、主题偏好。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/profile")
@Tag(name = "个人中心", description = "个人资料、修改密码、主题偏好")
public class SysProfileController {

    private final UserAppService userService;

    /**
     * 获取当前登录用户的个人资料。
     *
     * @return 个人资料
     */
    @GetMapping
    @Operation(summary = "获取个人资料")
    public R<ProfileResp> profile() {
        return R.ok(userService.getProfile(SecurityUtils.getUserId()));
    }

    /**
     * 修改个人资料。
     *
     * @param req 资料参数
     * @return 空响应
     */
    @PutMapping
    @Operation(summary = "修改个人资料")
    public R<Void> updateProfile(@Valid @RequestBody ProfileUpdateReq req) {
        userService.updateProfile(SecurityUtils.getUserId(), req);
        return R.ok();
    }

    /**
     * 修改密码（需校验原密码）。
     *
     * @param req 密码参数
     * @return 空响应
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "需校验原密码，修改成功后建议重新登录")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        userService.changePassword(SecurityUtils.getUserId(), req);
        return R.ok();
    }

    /**
     * 更新主题偏好（跨设备同步）。
     *
     * @param req 主题参数
     * @return 空响应
     */
    @PutMapping("/theme")
    @Operation(summary = "更新主题偏好", description = "light / dark / auto，保存后可跨设备同步")
    public R<Void> updateTheme(@Valid @RequestBody ThemeUpdateReq req) {
        userService.updateTheme(SecurityUtils.getUserId(), req.getTheme());
        return R.ok();
    }
}
