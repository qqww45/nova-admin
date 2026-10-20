package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统信息响应：仪表盘"关键参数"卡片用。
 *
 * <p>为什么只展示摘要（不暴露细节）：仪表盘给运营/管理员看大盘用，
 * 涉及敏感字段（如数据库连接密码、JWT 密钥）一律不出现在响应里。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@Schema(name = "SystemInfoResp", description = "系统信息")
public class SystemInfoResp {

    // ---- 应用 ----
    @Schema(description = "应用名称")
    private String appName;

    @Schema(description = "项目版本")
    private String version;

    @Schema(description = "当前激活的 profile（dev/prod）")
    private String activeProfile;

    @Schema(description = "启动时间")
    private LocalDateTime startTime;

    @Schema(description = "运行时长（人类可读，如 3 天 2 小时）")
    private String uptime;

    /** 关键开关（脱敏后）：verify.channel、verify.reveal-code（不暴露真实值） */
    @Schema(description = "关键开关（脱敏）")
    private Map<String, String> switches;

    // ---- JVM ----
    @Schema(description = "Java 版本")
    private String javaVersion;

    @Schema(description = "JVM 堆内存：已用 / 最大 / 初始（MB）")
    private MemoryUsage jvmHeap;

    @Schema(description = "JVM 非堆内存：已用（MB）")
    private MemoryUsage jvmNonHeap;

    @Schema(description = "可用处理器数")
    private int availableProcessors;

    // ---- 系统 ----
    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "系统架构")
    private String arch;

    @Schema(description = "工作目录")
    private String workDir;

    // ---- 数据库 ----
    @Schema(description = "数据库类型")
    private String databaseType;

    @Schema(description = "数据库驱动版本")
    private String driverVersion;

    @Schema(description = "连接池：活跃 / 空闲 / 最大")
    private PoolStats connectionPool;

    // ---- 业务 ----
    @Schema(description = "用户总数")
    private long userCount;

    @Schema(description = "角色总数")
    private long roleCount;

    @Schema(description = "部门总数")
    private long deptCount;

    @Schema(description = "今日登录日志条数")
    private long todayLoginLogCount;

    @Schema(description = "当前在线会话数")
    private long onlineSessionCount;

    // ----- 内嵌类型 -----

    @Data
    @Builder
    @Schema(description = "内存用量")
    public static class MemoryUsage {
        private final double usedMb;
        private final double maxMb;
        private final double initMb;
    }

    @Data
    @Builder
    @Schema(description = "连接池用量")
    public static class PoolStats {
        private final int active;
        private final int idle;
        private final int total;
        private final int max;
    }
}
