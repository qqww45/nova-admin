package com.example.admin.application.service;

import com.example.admin.interfaces.dto.resp.DeviceTrustResp;
import com.example.admin.interfaces.dto.resp.OnlineSessionResp;

import java.util.List;

/**
 * 在线用户与设备管理应用服务（可观测 + 处置）。
 *
 * <p>为什么要这个能力：登录安全不能只"防"，还要能"看"和"管"。
 * 管理员需要回答三个问题：现在谁在线？他们用什么设备登录的？可疑的那台能不能立刻踢掉？
 * 没有这三个能力，一旦发生账号泄露就只能改密（伤害面大），而无法精准处置。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface OnlineAppService {

    /**
     * 在线会话列表（一条 = 一个用户的一台设备）。
     *
     * @param username 用户名关键字（可为空）
     * @return 会话列表
     */
    List<OnlineSessionResp> list(String username);

    /**
     * 强制下线。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识（为空表示该用户所有设备）
     * @return 被下线的设备数量
     */
    int kick(Long userId, String deviceId);

    /**
     * 用户的可信设备列表（含是否在线）。
     *
     * @param userId 用户 ID
     * @return 可信设备列表
     */
    List<DeviceTrustResp> devices(Long userId);

    /**
     * 移除某台设备的信任（下次登录需重新验证）。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     */
    void removeTrust(Long userId, String deviceId);
}
