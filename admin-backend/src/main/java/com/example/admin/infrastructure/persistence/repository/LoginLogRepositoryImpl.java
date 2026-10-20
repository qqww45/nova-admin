package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.admin.domain.model.loginlog.LoginLog;
import com.example.admin.domain.repository.LoginLogRepository;
import com.example.admin.infrastructure.persistence.converter.LoginLogConverter;
import com.example.admin.infrastructure.persistence.mapper.SysLoginLogMapper;
import com.example.admin.infrastructure.persistence.po.SysLoginLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录日志仓储实现（Infrastructure 层）。
 *
 * <p>说明：趋势统计使用 {@code >= start} 的时间范围查询后在应用层按天聚合，
 * 避免使用 {@code DATE()} / {@code strftime()} 等数据库方言函数（跨库兼容要求）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class LoginLogRepositoryImpl implements LoginLogRepository {

    private final SysLoginLogMapper loginLogMapper;

    private final LoginLogConverter loginLogConverter;

    /**
     * 保存登录日志。
     *
     * <p>审计日志写入失败不应影响登录主流程，因此捕获异常仅记录错误日志。
     *
     * @param loginLog 日志领域对象
     */
    @Override
    public void save(LoginLog loginLog) {
        try {
            loginLogMapper.insert(loginLogConverter.toPo(loginLog));
        } catch (Exception e) {
            log.error("写入登录日志失败", e);
        }
    }

    /**
     * 查询最近 N 条登录记录。
     *
     * @param limit 条数
     * @return 登录记录列表
     */
    @Override
    public List<LoginLog> findRecent(int limit) {
        // limit 已做范围收敛（1-100），此处仅为数字，不存在 SQL 注入风险
        int size = Math.max(1, Math.min(limit, 100));
        List<SysLoginLog> pos = loginLogMapper.selectList(new LambdaQueryWrapper<SysLoginLog>()
                .orderByDesc(SysLoginLog::getLoginTime)
                .last("LIMIT " + size));
        List<LoginLog> result = new ArrayList<>();
        for (SysLoginLog po : pos) {
            result.add(loginLogConverter.toDomain(po));
        }
        return result;
    }

    /**
     * 统计今日登录次数。
     *
     * @param status 状态：0 成功 1 失败；null 表示全部
     * @return 次数
     */
    @Override
    public long countToday(Integer status) {
        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .ge(SysLoginLog::getLoginTime, start);
        if (status != null) {
            wrapper.eq(SysLoginLog::getStatus, status);
        }
        Long count = loginLogMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 查询指定时间之后的登录记录。
     *
     * @param start  起始时间
     * @param status 状态；null 表示全部
     * @return 登录记录列表
     */
    @Override
    public List<LoginLog> findSince(LocalDateTime start, Integer status) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .ge(SysLoginLog::getLoginTime, start);
        if (status != null) {
            wrapper.eq(SysLoginLog::getStatus, status);
        }
        List<LoginLog> result = new ArrayList<>();
        for (SysLoginLog po : loginLogMapper.selectList(wrapper)) {
            result.add(loginLogConverter.toDomain(po));
        }
        return result;
    }

    @Override
    public Map<String, Long> countGroupByDateSince(LocalDateTime start, Integer status) {
        // 分组计数交给数据库：调用方只需要"每天多少次"，不需要把每天的每一条都读出来。
        // DATE(login_time) 在 MySQL 与 SQLite 上都可用，这里是固定表达式，不拼接任何外部输入。
        QueryWrapper<SysLoginLog> wrapper = new QueryWrapper<SysLoginLog>()
                .select("DATE(login_time) AS day", "COUNT(*) AS cnt")
                .ge("login_time", start);
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.groupBy("DATE(login_time)");
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : loginLogMapper.selectMaps(wrapper)) {
            Object day = row.get("day");
            if (day == null) {
                continue;
            }
            Object cnt = row.get("cnt");
            result.put(day.toString(), cnt instanceof Number number ? number.longValue() : 0L);
        }
        return result;
    }
}
