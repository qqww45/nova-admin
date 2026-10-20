package com.example.admin.application.service.impl;

import com.example.admin.application.service.LoginLogAppService;
import com.example.admin.domain.model.loginlog.LoginLog;
import com.example.admin.domain.repository.LoginLogRepository;
import com.example.admin.common.util.SensitiveUtils;
import com.example.admin.interfaces.dto.resp.DashboardSummaryResp;
import com.example.admin.interfaces.dto.resp.LoginLogResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录日志应用服务实现。
 *
 * <p>趋势聚合在应用层按天完成：不使用 {@code DATE()}/{@code strftime()} 等数据库方言函数，
 * 保证 SQL 在 SQLite / MySQL / PostgreSQL 上行为一致。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogAppServiceImpl implements LoginLogAppService {

    /** 趋势日期展示格式 */
    private static final DateTimeFormatter TREND_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final LoginLogRepository loginLogRepository;

    /**
     * 保存登录日志。
     *
     * @param loginLog 日志领域对象
     */
    @Override
    public void save(LoginLog loginLog) {
        loginLogRepository.save(loginLog);
    }

    /**
     * 查询最近 N 条登录记录（IP 脱敏）。
     *
     * @param limit 条数
     * @return 登录记录列表
     */
    @Override
    public List<LoginLogResp> recent(int limit) {
        List<LoginLog> logs = loginLogRepository.findRecent(limit);
        List<LoginLogResp> result = new ArrayList<>();
        for (LoginLog item : logs) {
            LoginLogResp resp = new LoginLogResp();
            resp.setId(String.valueOf(item.getId()));
            resp.setUsername(item.getUsername());
            resp.setIp(SensitiveUtils.ip(item.getIp()));
            resp.setLocation(item.getLocation());
            resp.setBrowser(item.getBrowser());
            resp.setOs(item.getOs());
            resp.setStatus(item.getStatus());
            resp.setMsg(item.getMsg());
            resp.setLoginTime(item.getLoginTime());
            result.add(resp);
        }
        return result;
    }

    /**
     * 统计今日登录次数。
     *
     * @param status 状态
     * @return 次数
     */
    @Override
    public long countToday(Integer status) {
        return loginLogRepository.countToday(status);
    }

    /**
     * 统计近 N 日成功登录趋势（无数据的日期补 0，保证图表连续）。
     *
     * @param days 天数
     * @return 趋势点列表
     */
    @Override
    public List<DashboardSummaryResp.TrendPoint> loginTrend(int days) {
        LocalDateTime start = LocalDateTime.of(LocalDate.now().minusDays(days - 1L), LocalTime.MIN);
        // 分组与计数都在数据库完成，内存里只补"没有数据的日期"
        Map<String, Long> counted = loginLogRepository.countGroupByDateSince(start, 0);

        Map<String, Long> grouped = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).format(TREND_DATE_FORMATTER);
            grouped.put(date, counted.getOrDefault(date, 0L));
        }

        List<DashboardSummaryResp.TrendPoint> points = new ArrayList<>();
        grouped.forEach((date, value) -> {
            DashboardSummaryResp.TrendPoint point = new DashboardSummaryResp.TrendPoint();
            point.setDate(date);
            point.setValue(value);
            points.add(point);
        });
        return points;
    }
}
