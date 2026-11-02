package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集器绑定（nova 用户 + 金蝶账套 + 机器令牌）。
 *
 * <p>一个业务员可绑定多个账套，因此以 {@code (userId, acctId)} 为唯一键；
 * 令牌只存 SHA-256 摘要，原文仅在换取/刷新时返回一次。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdBinding {

    /** 主键 */
    private Long id;

    /** nova 用户 ID */
    private Long userId;

    /** 金蝶账套 ID */
    private String acctId;

    /** 账套名称（仅展示） */
    private String acctName;

    /** 金蝶登录账号（仅展示，密码不上行） */
    private String kdUsername;

    /** 金蝶服务器地址（仅展示） */
    private String kdServerUrl;

    /** 客户端机器名 */
    private String machineName;

    /** 客户端版本 */
    private String clientVersion;

    /** 机器令牌 SHA-256（十六进制） */
    private String tokenHash;

    /** 令牌前 8 位（页面辨认用） */
    private String tokenPrefix;

    /** 状态：ACTIVE / REVOKED */
    private String status;

    /** 最近一次带令牌请求时间 */
    private LocalDateTime lastUsedAt;

    /** 最近一次成功同步时间 */
    private LocalDateTime lastSyncAt;

    /** 最近一次同步结果摘要 */
    private String lastSyncResult;

    /** 客户水位 */
    private LocalDateTime watermarkCustomer;

    /** 出库水位 */
    private LocalDateTime watermarkOutbound;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否可用于鉴权。
     *
     * @return 状态为 ACTIVE
     */
    public boolean isActive() {
        return KdBindingStatus.ACTIVE.getCode().equalsIgnoreCase(status);
    }

    /**
     * 取较新的水位（只进不退，避免乱序上报把水位回拨导致重复拉取）。
     *
     * @param current 当前水位
     * @param incoming 本次上报水位
     * @return 较新者；两者都为空返回 null
     */
    public static LocalDateTime maxWatermark(LocalDateTime current, LocalDateTime incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return incoming.isAfter(current) ? incoming : current;
    }
}
