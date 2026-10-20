package com.example.admin.domain.service;

/**
 * 登录风控策略（领域服务接口）。
 *
 * <p>为什么定义在领域层：
 * "连续失败 N 次锁定账号 M 分钟""IP 级别限流"是业务安全规则而非技术细节，
 * 领域层声明规则，基础设施层用内存缓存（当前）或 Redis（后续）实现，
 * 替换为 Redis 时领域与应用层零改动。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface LoginPolicy {

    /**
     * 校验 IP 是否被限流。
     *
     * @param ip 客户端 IP
     * @throws IllegalStateException 被限流时抛出（携带提示码）
     */
    void checkIpLimit(String ip);

    /**
     * 校验账号是否处于锁定状态。
     *
     * @param username 用户名
     * @throws IllegalStateException 已锁定时抛出（携带提示码）
     */
    void checkAccountLocked(String username);

    /**
     * 记录一次登录失败（累计失败次数，达到阈值触发锁定）。
     *
     * @param username 用户名
     */
    void onLoginFailure(String username);

    /**
     * 登录成功，清除失败计数。
     *
     * @param username 用户名
     */
    void onLoginSuccess(String username);

    /**
     * 查询账号近期连续失败次数（供风险评级使用）。
     *
     * <p>为什么需要它：风险分级要能做到"越打越难" ——
     * 失败序列本身就是最强的风险信号之一，攻击者每失败一次，下一次就该更难。
     *
     * @param username 用户名
     * @return 窗口期内的连续失败次数
     */
    int failureCount(String username);
}
