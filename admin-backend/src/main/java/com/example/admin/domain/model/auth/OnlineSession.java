package com.example.admin.domain.model.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 在线会话（值对象）：一次"设备级登录"的运行时快照。
 *
 * <p>为什么按"用户 + 设备"而不是只按用户：
 * 同一个账号常同时在手机、办公机、家里登录，管理员需要能看清"是哪一台"，
 * 并且只踢出可疑的那一台，而不是把人全部赶下线（低打扰原则同样适用于处置）。
 *
 * <p>为什么记录 accessJti：
 * 强制下线要求"立即生效"，只有把当前 access 令牌也拉黑才能做到；
 * 否则被踢的用户在 access 令牌剩余有效期内仍可继续操作。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class OnlineSession {

    /** 用户 ID */
    private final Long userId;

    /** 用户名 */
    private final String username;

    /** 昵称 */
    private final String nickname;

    /** 设备标识 */
    private final String deviceId;

    /** 登录 IP */
    private final String ip;

    /** 浏览器 */
    private final String browser;

    /** 操作系统 */
    private final String os;

    /** 当前 access 令牌的 jti（用于强制下线时精准拉黑） */
    private String accessJti;

    /** 登录时间 */
    private final LocalDateTime loginTime;

    /** 最近活跃时间（刷新令牌时更新） */
    private LocalDateTime lastAccessTime;

    public OnlineSession(Long userId, String username, String nickname, String deviceId,
                         String ip, String browser, String os, String accessJti) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.deviceId = deviceId;
        this.ip = ip;
        this.browser = browser;
        this.os = os;
        this.accessJti = accessJti;
        this.loginTime = LocalDateTime.now();
        this.lastAccessTime = this.loginTime;
    }

    /**
     * 全量构造（含登录时间与最近活跃时间），供持久化还原使用。
     *
     * <p>为什么单独提供一个构造器：业务构造时登录时间就是"现在"，
     * 但从 Redis 还原时必须是<b>原来的时间</b>，否则每次读出来都变成"刚刚登录"，
     * 在线列表的登录时长、最近活跃排序会全部失真。
     *
     * <p>{@code @JsonCreator} 只服务于 Redis 实现的反序列化，不影响业务语义。
     */
    @JsonCreator
    public OnlineSession(@JsonProperty("userId") Long userId,
                         @JsonProperty("username") String username,
                         @JsonProperty("nickname") String nickname,
                         @JsonProperty("deviceId") String deviceId,
                         @JsonProperty("ip") String ip,
                         @JsonProperty("browser") String browser,
                         @JsonProperty("os") String os,
                         @JsonProperty("accessJti") String accessJti,
                         @JsonProperty("loginTime") LocalDateTime loginTime,
                         @JsonProperty("lastAccessTime") LocalDateTime lastAccessTime) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.deviceId = deviceId;
        this.ip = ip;
        this.browser = browser;
        this.os = os;
        this.accessJti = accessJti;
        this.loginTime = loginTime == null ? LocalDateTime.now() : loginTime;
        this.lastAccessTime = lastAccessTime == null ? this.loginTime : lastAccessTime;
    }

    /**
     * 更新最近活跃时间（刷新令牌时调用）。
     */
    public void touch() {
        this.lastAccessTime = LocalDateTime.now();
    }

    /**
     * 更新当前 access 令牌标识（刷新后旧令牌已被拉黑，需同步为新 jti）。
     *
     * @param accessJti 新的 access 令牌 jti
     */
    public void updateAccessJti(String accessJti) {
        this.accessJti = accessJti;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getIp() {
        return ip;
    }

    public String getBrowser() {
        return browser;
    }

    public String getOs() {
        return os;
    }

    public String getAccessJti() {
        return accessJti;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public LocalDateTime getLastAccessTime() {
        return lastAccessTime;
    }
}
