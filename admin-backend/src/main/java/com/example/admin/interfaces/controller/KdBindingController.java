package com.example.admin.interfaces.controller;

import com.example.admin.application.service.KdBindingAppService;
import com.example.admin.application.service.KdCommandAppService;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.KdBindingCodeReq;
import com.example.admin.interfaces.dto.req.KdBindingRedeemReq;
import com.example.admin.interfaces.dto.req.KdCommandSendReq;
import com.example.admin.interfaces.dto.resp.KdBindingCodeResp;
import com.example.admin.interfaces.dto.resp.KdBindingRedeemResp;
import com.example.admin.interfaces.dto.resp.KdBindingResp;
import com.example.admin.interfaces.dto.resp.KdCommandResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采集器绑定控制器。
 *
 * <p>{@code /kd/binding/redeem} 是白名单接口：客户端此时还没有令牌，
 * 安全性由一次性绑定码保证（5 分钟、一次一用、只存摘要）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/kd/binding")
@Tag(name = "金蝶绑定", description = "绑定码、机器令牌与吊销")
public class KdBindingController {

    private final KdBindingAppService bindingService;
    private final KdCommandAppService commandService;

    /**
     * 查询绑定列表。
     *
     * @return 绑定列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('kd:binding:list')")
    @Operation(summary = "查询绑定列表")
    public R<List<KdBindingResp>> list() {
        return R.ok(bindingService.list());
    }

    /**
     * 生成一次性绑定码。
     *
     * @param req 请求
     * @return 绑定码
     */
    @PostMapping("/code")
    @PreAuthorize("@ss.hasPermi('kd:binding:code')")
    @Operation(summary = "生成绑定码（5 分钟有效）")
    public R<KdBindingCodeResp> createCode(@RequestBody(required = false) KdBindingCodeReq req) {
        return R.ok(bindingService.createCode(req));
    }

    /**
     * 客户端用绑定码换取机器令牌（无需登录态）。
     *
     * @param req 请求
     * @return 令牌
     */
    @PostMapping("/redeem")
    @Operation(summary = "绑定码换取机器令牌")
    public R<KdBindingRedeemResp> redeem(@Valid @RequestBody KdBindingRedeemReq req) {
        return R.ok(bindingService.redeem(req));
    }

    /**
     * 吊销令牌。
     *
     * @param id 绑定 ID
     * @return 操作结果
     */
    @PutMapping("/{id}/revoke")
    @PreAuthorize("@ss.hasPermi('kd:binding:revoke')")
    @Operation(summary = "吊销令牌")
    public R<Void> revoke(@PathVariable String id) {
        bindingService.revoke(id);
        return R.ok();
    }

    /**
     * 轮换令牌（水位与历史数据保留）。
     *
     * @param id 绑定 ID
     * @return 新令牌
     */
    @PostMapping("/{id}/refresh")
    @PreAuthorize("@ss.hasPermi('kd:binding:code')")
    @Operation(summary = "轮换令牌")
    public R<KdBindingRedeemResp> refresh(@PathVariable String id) {
        return R.ok(bindingService.refresh(id));
    }

    /**
     * 给采集器下发指令（立即同步 / 重载配置 / 吊销 / 升级）。
     *
     * @param id 绑定 ID
     * @param req 指令
     * @return 已入队的指令
     */
    @PostMapping("/{id}/command")
    @PreAuthorize("@ss.hasPermi('kd:binding:command')")
    @Operation(summary = "下发采集器指令")
    public R<KdCommandResp> sendCommand(@PathVariable String id,
                                        @Valid @RequestBody KdCommandSendReq req) {
        return R.ok(commandService.send(id, req));
    }

    /**
     * 最近指令（含执行结果）。
     *
     * @param id 绑定 ID
     * @return 指令列表
     */
    @GetMapping("/{id}/commands")
    @PreAuthorize("@ss.hasPermi('kd:binding:list')")
    @Operation(summary = "最近指令")
    public R<List<KdCommandResp>> commands(@PathVariable String id) {
        return R.ok(commandService.recent(id));
    }
}
