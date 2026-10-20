package com.example.admin.application.service;

import com.example.admin.interfaces.dto.resp.DashboardSummaryResp;
import com.example.admin.interfaces.dto.resp.LoginLogResp;
import com.example.admin.interfaces.dto.resp.SystemInfoResp;

import java.util.List;

/**
 * 仪表盘应用服务（用例编排层）：汇总统计与最近登录。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface DashboardAppService {

    /**
     * 获取首页汇总统计。
     *
     * @return 汇总数据
     */
    DashboardSummaryResp summary();

    /**
     * 获取最近登录记录。
     *
     * @param limit 条数
     * @return 登录记录列表
     */
    List<LoginLogResp> recentLogin(int limit);

    /**
     * 获取系统信息与关键运行参数（仪表"系统参数"卡片）。
     *
     * <p>只返回非敏感的运行时指标：JVM、OS、数据库类型、连接池、业务计数与关键开关。
     * 密码、密钥、数据库连接串一律不出现在响应中。
     *
     * @return 系统信息
     */
    SystemInfoResp systemInfo();
}
