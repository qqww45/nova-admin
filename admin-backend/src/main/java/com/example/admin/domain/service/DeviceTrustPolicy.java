package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.DeviceFingerprint;
import com.example.admin.domain.model.auth.DeviceTrust;

import java.time.Duration;
import java.util.List;

/**
 * 可信设备策略（领域接口）。
 *
 * <p>为什么放在领域层：
 * "陌生设备需要二次校验、可信设备免打扰"是<b>业务规则</b>而不是技术细节，
 * 具体用什么存储（Redis / 内存 / 数据库）由基础设施决定。
 * 这样将来把内存实现换成 Redis 时，应用层与领域层零改动。
 *
 * <p>存储设计（Redis 实现时）：
 * <pre>
 * Key   : device:trust:{userId}:{deviceId}
 * Value : 信任时间
 * TTL   : 30 天（到期后重新走一次验证码，避免"一次信任、永久免检"）
 * </pre>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface DeviceTrustPolicy {

    /** 默认信任有效期 */
    Duration DEFAULT_TRUST_TTL = Duration.ofDays(30);

    /**
     * 计算设备标识（同一台设备、同一个浏览器应稳定得到相同结果）。
     *
     * @param fingerprint 设备指纹
     * @return deviceId
     */
    String resolveDeviceId(DeviceFingerprint fingerprint);

    /**
     * 判断该设备对用户是否可信。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return true 表示可跳过二次校验
     */
    boolean isTrusted(Long userId, String deviceId);

    /**
     * 标记设备为可信（二次校验通过后调用）。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     */
    void trust(Long userId, String deviceId);

    /**
     * 撤销单个设备的信任（用户"移除此设备"或风险处置时使用）。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     */
    void revoke(Long userId, String deviceId);

    /**
     * 撤销用户全部设备的信任（改密、账号被盗等场景）。
     *
     * @param userId 用户 ID
     * @return 被撤销的设备数量
     */
    int revokeAll(Long userId);

    /**
     * 列出用户的可信设备（供"设备管理"页面展示）。
     *
     * <p>为什么要让用户看见：安全能力如果不可见、不可控，用户就无法判断
     * "是不是有人在我不知道的设备上记住了登录"，也就失去了自我保护的手段。
     *
     * @param userId 用户 ID
     * @return 可信设备列表
     */
    List<DeviceTrust> listTrusted(Long userId);
}
