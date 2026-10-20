package com.example.admin.application.service.impl;

import com.example.admin.application.service.DashboardAppService;
import com.example.admin.application.service.LoginLogAppService;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.domain.repository.MenuRepository;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.service.OnlineSessionStore;
import com.example.admin.interfaces.dto.resp.DashboardSummaryResp;
import com.example.admin.interfaces.dto.resp.LoginLogResp;
import com.example.admin.interfaces.dto.resp.SystemInfoResp;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘应用服务实现：只做数据聚合，不承载业务规则。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardAppServiceImpl implements DashboardAppService {

    /** 最近登录记录默认条数 */
    private static final int DEFAULT_RECENT_LIMIT = 10;

    /** 趋势统计天数 */
    private static final int TREND_DAYS = 7;

    private static final double MB = 1024.0 * 1024.0;

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final MenuRepository menuRepository;

    private final DeptRepository deptRepository;

    private final LoginLogAppService loginLogAppService;

    private final OnlineSessionStore onlineSessionStore;

    private final Environment environment;

    private final DataSource dataSource;

    /** 应用版本（未配置时回退 dev，避免页面上出现 null） */
    @Value("${admin.version:dev}")
    private String appVersion;

    /** 数据库 URL（仅用于解析数据库类型，不返回给前端） */
    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    /** 驱动类名（展示用） */
    @Value("${spring.datasource.driver-class-name:}")
    private String driverClassName;

    /** 验证码下发通道 */
    @Value("${admin.verify.channel:log}")
    private String verifyChannel;

    /**
     * 获取首页汇总统计。
     *
     * @return 汇总数据
     */
    @Override
    public DashboardSummaryResp summary() {
        DashboardSummaryResp resp = new DashboardSummaryResp();
        resp.setUserCount(userRepository.count());
        resp.setRoleCount(roleRepository.count());
        resp.setMenuCount(menuRepository.count());
        resp.setDeptCount(deptRepository.count());
        resp.setTodayLoginCount(loginLogAppService.countToday(0));
        resp.setTodayFailCount(loginLogAppService.countToday(1));
        resp.setLoginTrend(loginLogAppService.loginTrend(TREND_DAYS));
        return resp;
    }

    /**
     * 获取最近登录记录。
     *
     * @param limit 条数
     * @return 登录记录列表
     */
    @Override
    public List<LoginLogResp> recentLogin(int limit) {
        int size = limit <= 0 ? DEFAULT_RECENT_LIMIT : Math.min(limit, 100);
        return loginLogAppService.recent(size);
    }

    /**
     * 获取系统信息与关键运行参数。
     *
     * @return 系统信息
     */
    @Override
    public SystemInfoResp systemInfo() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long uptimeMs = runtimeBean.getUptime();

        return SystemInfoResp.builder()
                // 应用
                .appName(environment.getProperty("spring.application.name", "admin-backend"))
                .version(appVersion)
                .activeProfile(String.join(",", environment.getActiveProfiles()))
                .startTime(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(runtimeBean.getStartTime()), ZoneId.systemDefault()))
                .uptime(formatUptime(uptimeMs))
                .switches(buildSwitches())
                // JVM
                .javaVersion(System.getProperty("java.version"))
                .jvmHeap(toUsage(memoryBean.getHeapMemoryUsage()))
                .jvmNonHeap(toUsage(memoryBean.getNonHeapMemoryUsage()))
                .availableProcessors(Runtime.getRuntime().availableProcessors())
                // 系统
                .os(System.getProperty("os.name") + " " + System.getProperty("os.version"))
                // 不再返回 os.arch 与 user.dir：CPU 架构与绝对路径属于主机指纹，
                // 对运维排障价值有限，却是内网横向与定向利用的高价值侦察数据
                // 数据库
                .databaseType(parseDatabaseType(jdbcUrl))
                .driverVersion(driverClassName)
                .connectionPool(buildPoolStats())
                // 业务
                .userCount(userRepository.count())
                .roleCount(roleRepository.count())
                .deptCount(deptRepository.count())
                .todayLoginLogCount(loginLogAppService.countToday(0) + loginLogAppService.countToday(1))
                .onlineSessionCount(onlineSessionStore.listAll().size())
                .build();
    }

    /**
     * 关键开关（脱敏）：只暴露"用了哪种策略"，不暴露任何凭据与密钥。
     */
    private Map<String, String> buildSwitches() {
        Map<String, String> switches = new LinkedHashMap<>();
        switches.put("verify.channel", verifyChannel);
        switches.put("verify.reveal-code", environment.getProperty("admin.verify.reveal-code", "false"));
        switches.put("config.cache", dataSource != null ? "in-memory" : "unavailable");
        return switches;
    }

    /**
     * 连接池统计：仅 HikariCP 能取到（Spring Boot 默认连接池）。
     */
    private SystemInfoResp.PoolStats buildPoolStats() {
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                return SystemInfoResp.PoolStats.builder()
                        .active(pool.getActiveConnections())
                        .idle(pool.getIdleConnections())
                        .total(pool.getTotalConnections())
                        .max(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 0))
                        .build();
            }
        }
        return SystemInfoResp.PoolStats.builder().active(0).idle(0).total(0).max(0).build();
    }

    /**
     * 从 JDBC URL 解析数据库类型（不返回连接串本身）。
     */
    private String parseDatabaseType(String url) {
        if (url == null || url.isBlank()) {
            return "unknown";
        }
        String lower = url.toLowerCase();
        if (lower.contains("sqlite")) {
            return "SQLite";
        }
        if (lower.contains("mysql")) {
            return "MySQL";
        }
        if (lower.contains("postgresql")) {
            return "PostgreSQL";
        }
        return "unknown";
    }

    private SystemInfoResp.MemoryUsage toUsage(MemoryUsage usage) {
        return SystemInfoResp.MemoryUsage.builder()
                .usedMb(round(usage.getUsed() / MB))
                .maxMb(round(usage.getMax() / MB))
                .initMb(round(usage.getInit() / MB))
                .build();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * 运行时长格式化：3 天 2 小时 5 分。
     */
    private String formatUptime(long uptimeMs) {
        Duration d = Duration.ofMillis(uptimeMs);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" 天 ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append(" 小时 ");
        }
        sb.append(minutes).append(" 分");
        return sb.toString();
    }
}
