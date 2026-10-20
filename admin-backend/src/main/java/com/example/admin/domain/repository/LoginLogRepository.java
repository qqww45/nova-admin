package com.example.admin.domain.repository;

import com.example.admin.domain.model.loginlog.LoginLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志仓储接口（领域层定义）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface LoginLogRepository {

    /**
     * 保存登录日志。
     *
     * @param loginLog 日志领域对象
     */
    void save(LoginLog loginLog);

    /**
     * 查询最近 N 条登录记录。
     *
     * @param limit 条数
     * @return 登录记录列表（按时间倒序）
     */
    List<LoginLog> findRecent(int limit);

    /**
     * 统计今日登录次数。
     *
     * @param status 状态：0 成功 1 失败；null 表示全部
     * @return 次数
     */
    long countToday(Integer status);

    /**
     * 查询指定时间之后的登录记录（趋势统计用）。
     *
     * @param start  起始时间
     * @param status 状态；null 表示全部
     * @return 登录记录列表
     */
    List<LoginLog> findSince(LocalDateTime start, Integer status);

    /**
     * 按日期分组统计指定时间之后的登录次数（趋势图用）。
     *
     * <p>为什么不直接返回列表让调用方分组：登录日志是增长最快的表，
     * 把区间内所有行拉进内存只为数个数，数据量一大就会拖垮应用。
     *
     * @param start  起始时间
     * @param status 状态；null 表示全部
     * @return 日期（yyyy-MM-dd）→ 次数
     */
    java.util.Map<String, Long> countGroupByDateSince(LocalDateTime start, Integer status);
}
