package com.example.admin.interfaces.controller;

import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.resp.DashboardSummaryResp;
import com.example.admin.interfaces.dto.resp.LoginLogResp;
import com.example.admin.interfaces.dto.resp.SystemInfoResp;
import com.example.admin.application.service.DashboardAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仪表盘控制器：首页统计与最近登录记录。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/dashboard")
@Tag(name = "仪表盘", description = "首页统计数据与最近登录记录")
public class DashboardController {

    private final DashboardAppService dashboardService;

    /**
     * 获取首页汇总统计。
     *
     * @return 汇总数据
     */
    // 仪表盘此前三个端点都没有方法级鉴权，只靠 anyRequest().authenticated()，
    // 意味着任何登录用户（含只有 dashboard:view 的普通用户）都能拉取系统指纹与全量登录审计。
    @GetMapping("/summary")
    @PreAuthorize("@ss.hasPermi('dashboard:view')")
    @Operation(summary = "首页汇总统计", description = "用户/角色/菜单/部门数量与近 7 日登录趋势")
    public R<DashboardSummaryResp> summary() {
        return R.ok(dashboardService.summary());
    }

    /**
     * 获取最近登录记录。
     *
     * @param limit 条数，默认 10
     * @return 登录记录列表
     */
    @GetMapping("/recentLogin")
    @PreAuthorize("@ss.hasPermi('dashboard:view')")
    @Operation(summary = "最近登录记录", description = "审计用途，登录用户均可查看（数据已脱敏）")
    public R<List<LoginLogResp>> recentLogin(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(dashboardService.recentLogin(limit));
    }

    /**
     * 获取系统信息与关键运行参数。
     *
     * @return 系统信息（JVM / OS / 数据库 / 业务计数 / 关键开关）
     */
    // 系统指纹（OS/架构/路径/连接池）只给运维看：它是内网横向与定向漏洞利用的高价值侦察数据
    @GetMapping("/systemInfo")
    @PreAuthorize("@ss.hasPermi('system:monitor:list')")
    @Operation(summary = "系统信息与关键参数", description = "仪表盘展示用；不含任何凭据与密钥")
    public R<SystemInfoResp> systemInfo() {
        return R.ok(dashboardService.systemInfo());
    }
}
