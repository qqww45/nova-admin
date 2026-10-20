package com.example.admin.domain.model.loginlog;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志（领域模型）：记录登录行为，用于审计与仪表盘统计。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class LoginLog {

    /** 主键 */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录 IP */
    private String ip;

    /** 登录地点 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 状态：0 成功 1 失败 */
    private Integer status;

    /** 提示信息 */
    private String msg;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /**
     * 创建一条成功登录记录。
     *
     * @param username 登录账号
     * @param ip       IP
     * @param browser  浏览器
     * @param os       操作系统
     * @return 登录日志领域对象
     */
    public static LoginLog success(String username, String ip, String browser, String os) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setIp(ip);
        log.setBrowser(browser);
        log.setOs(os);
        log.setStatus(0);
        log.setMsg("登录成功");
        log.setLoginTime(LocalDateTime.now());
        return log;
    }

    /**
     * 创建一条失败登录记录。
     *
     * @param username 登录账号
     * @param ip       IP
     * @param browser  浏览器
     * @param os       操作系统
     * @param reason   失败原因（仅内部记录，不返回前端）
     * @return 登录日志领域对象
     */
    public static LoginLog failure(String username, String ip, String browser, String os, String reason) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setIp(ip);
        log.setBrowser(browser);
        log.setOs(os);
        log.setStatus(1);
        log.setMsg(reason);
        log.setLoginTime(LocalDateTime.now());
        return log;
    }
}
