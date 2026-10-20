package com.example.admin.application.service.impl;

import com.example.admin.application.service.OnlineAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.domain.model.auth.OnlineSession;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.service.DataScopePolicy;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.service.DeviceTrustPolicy;
import com.example.admin.domain.service.OnlineSessionStore;
import com.example.admin.domain.port.TokenBlacklist;
import com.example.admin.domain.port.TokenIssuer;
import com.example.admin.domain.service.RefreshTokenStore;
import com.example.admin.interfaces.dto.resp.DeviceTrustResp;
import com.example.admin.interfaces.dto.resp.OnlineSessionResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线用户与设备管理实现。
 *
 * <p>处置动作（强制下线）为什么必须做三件事：
 * <ol>
 *     <li><b>拉黑 access 令牌</b>：否则被踢的人在 access 剩余有效期内还能继续操作，"踢"就形同虚设；</li>
 *     <li><b>删除 refresh 令牌</b>：断掉无感续期，令牌一过期就必须重新登录；</li>
 *     <li><b>撤销设备信任</b>：防止可疑设备下次直接免验证登录。</li>
 * </ol>
 * 三者缺一，都会留下"看起来踢了、实际还能用"的假象。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineAppServiceImpl implements OnlineAppService {

    private final OnlineSessionStore onlineSessionStore;

    private final RefreshTokenStore refreshTokenStore;

    private final DeviceTrustPolicy deviceTrustPolicy;

    private final TokenBlacklist tokenBlacklist;

    private final TokenIssuer tokenIssuer;

    private final DataScopePolicy dataScopePolicy;

    private final OperatorContext operatorContext;

    private final UserRepository userRepository;

    private final DeptRepository deptRepository;

    @Override
    public List<OnlineSessionResp> list(String username) {
        List<OnlineSession> sessions = onlineSessionStore.listAll().stream()
                .filter(session -> username == null || username.isBlank()
                        || session.getUsername().contains(username)
                        || (session.getNickname() != null && session.getNickname().contains(username)))
                .toList();
        return toRespList(sessions);
    }

    @Override
    public int kick(Long userId, String deviceId) {
        // 强制下线同样要受数据范围约束：能把谁踢下线，就该和能看见谁保持一致
        assertUserInDataScope(userId);
        List<OnlineSession> targets = resolveTargets(userId, deviceId);

        // 1. access 令牌拉黑 → 立即失效（最长 2 小时的窗口被封死）
        LocalDateTime accessExpire = LocalDateTime.now().plusSeconds(tokenIssuer.accessTokenTtlSeconds());
        for (OnlineSession session : targets) {
            tokenBlacklist.add(session.getAccessJti(), Constants.TOKEN_TYPE_ACCESS, userId, accessExpire);
        }

        // 2. refresh 令牌删除 → 无法续期；3. 设备信任撤销 → 下次必须重新验证
        boolean allDevices = deviceId == null || deviceId.isBlank();
        if (allDevices) {
            refreshTokenStore.removeByUser(userId);
            onlineSessionStore.removeByUser(userId);
            deviceTrustPolicy.revokeAll(userId);
        } else {
            refreshTokenStore.remove(userId, deviceId);
            onlineSessionStore.remove(userId, deviceId);
            deviceTrustPolicy.revoke(userId, deviceId);
        }
        log.warn("强制下线：userId={} deviceId={} 涉及设备 {} 台", userId, deviceId, targets.size());
        return targets.size();
    }

    @Override
    public List<DeviceTrustResp> devices(Long userId) {
        Set<String> onlineDevices = onlineSessionStore.listByUser(userId).stream()
                .map(OnlineSession::getDeviceId)
                .collect(Collectors.toSet());
        return deviceTrustPolicy.listTrusted(userId).stream()
                .map(trust -> DeviceTrustResp.builder()
                        .deviceId(trust.getDeviceId())
                        .trustTime(trust.getTrustTime())
                        .expireTime(trust.getExpireTime())
                        .online(onlineDevices.contains(trust.getDeviceId()))
                        .build())
                .toList();
    }

    @Override
    public void removeTrust(Long userId, String deviceId) {
        assertUserInDataScope(userId);
        // 只撤销信任、不踢下线：这样"移除信任"的语义单一明确（下次登录需重新验证），
        // 需要立即断开时应使用"强制下线"，避免两个动作互相干扰导致预期不清
        deviceTrustPolicy.revoke(userId, deviceId);
        log.info("已移除可信设备：userId={} deviceId={}", userId, deviceId);
    }

    /**
     * 校验目标用户是否在操作者的数据可见范围内。
     *
     * <p>强制下线与移除信任都是"按 ID 操作某个用户"的入口，
     * 若不加这一道，看不到某个人的部门管理员照样能把对方踢下线。
     *
     * @param targetUserId 目标用户 ID
     * @throws BusinessException 超出数据范围时抛出
     */
    private void assertUserInDataScope(Long targetUserId) {
        Map<Long, Long> deptMap = userRepository.findDeptIdMapByUserIds(List.of(targetUserId));
        if (!dataScopePolicy.canAccessUser(operatorContext.current(), targetUserId, deptMap.get(targetUserId))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该用户（超出数据范围）");
        }
    }

    /**
     * 解析要处置的会话（指定设备或该用户全部设备）。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识（空表示全部）
     * @return 目标会话列表
     */
    private List<OnlineSession> resolveTargets(Long userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return onlineSessionStore.listByUser(userId);
        }
        OnlineSession session = onlineSessionStore.get(userId, deviceId);
        return session == null ? List.of() : List.of(session);
    }

    /**
     * 会话 → 响应（补齐部门名与设备信任状态）。
     *
     * @param session 会话
     * @return 响应
     */
    /**
     * 批量装配会话响应。
     *
     * <p>为什么要先批量取部门名：原先每条会话各查一次用户表 + 一次部门表（N+1），
     * 50 台在线设备就是 100 次查询，接口耗时随在线数线性增长。
     * 改成"一次取用户部门、一次取部门名"后，查询次数恒定为 2。
     */
    private List<OnlineSessionResp> toRespList(List<OnlineSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = sessions.stream()
                .map(OnlineSession::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Long> deptIdByUser = userRepository.findDeptIdMapByUserIds(userIds);
        Map<Long, String> deptNames = deptRepository.findNamesByIds(deptIdByUser.values());
        return sessions.stream()
                .map(session -> {
                    Long deptId = deptIdByUser.get(session.getUserId());
                    return toResp(session, deptId == null ? "" : deptNames.getOrDefault(deptId, ""));
                })
                .toList();
    }

    private OnlineSessionResp toResp(OnlineSession session, String deptName) {
        return OnlineSessionResp.builder()
                .userId(String.valueOf(session.getUserId()))
                .username(session.getUsername())
                .nickname(session.getNickname())
                .deptName(deptName)
                .ip(session.getIp())
                .browser(session.getBrowser())
                .os(session.getOs())
                .deviceId(session.getDeviceId())
                .trusted(deviceTrustPolicy.isTrusted(session.getUserId(), session.getDeviceId()))
                .loginTime(session.getLoginTime())
                .lastAccessTime(session.getLastAccessTime())
                .build();
    }
}
