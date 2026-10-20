package com.example.admin.domain.model.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 可信设备记录（值对象）：某用户在某台设备上的信任状态。
 *
 * <p>展示给用户的意义：让用户能看见"哪些设备被记住了"，
 * 并对不再使用或可疑的设备执行移除 —— 安全功能必须可见、可控，否则等于黑盒。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class DeviceTrust {

    /** 设备标识 */
    private final String deviceId;

    /** 建立信任的时间 */
    private final LocalDateTime trustTime;

    /** 信任到期时间（到期后需重新过一次验证码） */
    private final LocalDateTime expireTime;

    /**
     * 构造信任记录。
     *
     * <p>{@code @JsonCreator}：字段均为 final，Redis 实现需要它能从 JSON 还原。
     */
    @JsonCreator
    public DeviceTrust(@JsonProperty("deviceId") String deviceId,
                       @JsonProperty("trustTime") LocalDateTime trustTime,
                       @JsonProperty("expireTime") LocalDateTime expireTime) {
        this.deviceId = deviceId;
        this.trustTime = trustTime;
        this.expireTime = expireTime;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public LocalDateTime getTrustTime() {
        return trustTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }
}
