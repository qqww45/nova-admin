package com.example.admin.application.service;

import com.example.admin.domain.model.loginlog.LoginLog;
import com.example.admin.interfaces.dto.resp.DashboardSummaryResp;
import com.example.admin.interfaces.dto.resp.LoginLogResp;

import java.util.List;

/**
 * 登录日志应用服务（用例编排层）：审计记录与仪表盘统计。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface LoginLogAppService {

    /**
     * 保存登录日志。
     *
     * @param loginLog 日志领域对象
     */
    void save(LoginLog loginLog);

    /**
     * 查询最近 N 条登录记录（IP 已脱敏）。
     *
     * @param limit 条数
     * @return 登录记录列表
     */
    List<LoginLogResp> recent(int limit);

    /**
     * 统计今日登录次数。
     *
     * @param status 状态：0 成功 1 失败；null 表示全部
     * @return 次数
     */
    long countToday(Integer status);

    /**
     * 统计近 N 日登录趋势（按天聚合）。
     *
     * @param days 天数
     * @return 趋势点列表
     */
    List<DashboardSummaryResp.TrendPoint> loginTrend(int days);
}
