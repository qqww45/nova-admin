package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘汇总数据。
 *
 * <p>说明：访问趋势等统计目前基于登录日志真实统计；
 * 若后续接入 PV/UV 埋点，只需替换 Service 的数据来源，本结构保持不变。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "DashboardSummaryResp", description = "仪表盘汇总数据")
public class DashboardSummaryResp {

    /** 用户总数 */
    @Schema(description = "用户总数")
    private Long userCount;

    /** 角色总数 */
    @Schema(description = "角色总数")
    private Long roleCount;

    /** 菜单总数 */
    @Schema(description = "菜单总数")
    private Long menuCount;

    /** 部门总数 */
    @Schema(description = "部门总数")
    private Long deptCount;

    /** 今日登录次数 */
    @Schema(description = "今日登录次数")
    private Long todayLoginCount;

    /** 今日登录失败次数 */
    @Schema(description = "今日登录失败次数")
    private Long todayFailCount;

    /** 近 7 日登录趋势 */
    @Schema(description = "近7日登录趋势")
    private List<TrendPoint> loginTrend = new ArrayList<>();

    /**
     * 趋势点。
     */
    @Data
    @Schema(name = "TrendPoint", description = "趋势点")
    public static class TrendPoint {

        /** 日期（MM-dd） */
        @Schema(description = "日期", example = "10-19")
        private String date;

        /** 数值 */
        @Schema(description = "数值")
        private Long value;
    }
}
