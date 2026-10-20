package com.example.admin.interfaces.controller;

import com.example.admin.application.service.OnlineAppService;
import com.example.admin.common.util.IdParser;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.KickReq;
import com.example.admin.interfaces.dto.resp.DeviceTrustResp;
import com.example.admin.interfaces.dto.resp.OnlineSessionResp;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线用户与设备管理控制器。
 *
 * <p>权限说明：与用户管理一致，每个接口都带 {@code @PreAuthorize}，
 * 前端隐藏按钮只作体验优化，真正的边界在后端。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/online")
@Tag(name = "在线用户", description = "在线会话与可信设备管理")
public class OnlineController {

    private final OnlineAppService onlineService;

    /**
     * 在线会话列表。
     *
     * @param username 用户名/昵称关键字（可空）
     * @return 会话列表（一条代表一个用户的一台设备）
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:online:list')")
    @Operation(summary = "在线会话列表", description = "一条记录 = 一个用户的一台设备")
    public R<List<OnlineSessionResp>> list(@RequestParam(required = false) String username) {
        return R.ok(onlineService.list(username));
    }

    /**
     * 强制下线。
     *
     * @param req 用户 ID 与设备标识（设备为空表示全部设备）
     * @return 被下线的设备数量
     */
    @PostMapping("/kick")
    @PreAuthorize("@ss.hasPermi('system:online:kick')")
    @Operation(summary = "强制下线", description = "立即拉黑 access 令牌、删除 refresh 令牌并撤销设备信任")
    public R<Integer> kick(@Valid @RequestBody KickReq req) {
        return R.ok(onlineService.kick(IdParser.parseId(req.getUserId()), req.getDeviceId()));
    }

    /**
     * 查询某用户的可信设备（含是否在线）。
     *
     * @param userId 用户 ID
     * @return 可信设备列表
     */
    @GetMapping("/devices/{userId}")
    @PreAuthorize("@ss.hasPermi('system:online:list')")
    @Operation(summary = "可信设备列表", description = "已记住的设备，移除后下次登录需重新验证")
    public R<List<DeviceTrustResp>> devices(@PathVariable String userId) {
        return R.ok(onlineService.devices(IdParser.parseId(userId)));
    }

    /**
     * 移除可信设备。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return 空
     */
    @DeleteMapping("/device/{userId}/{deviceId}")
    @PreAuthorize("@ss.hasPermi('system:online:trust:remove')")
    @Operation(summary = "移除可信设备", description = "仅撤销信任，不强制下线；立即断开请用强制下线")
    public R<Void> removeTrust(@PathVariable String userId, @PathVariable String deviceId) {
        onlineService.removeTrust(IdParser.parseId(userId), deviceId);
        return R.ok();
    }
}
