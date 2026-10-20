package com.example.admin.domain.model.auth;

/**
 * 登录风险信号（值对象）：风险评级所需的输入集合。
 *
 * <p>取值原则：只用"判定必需的最小集合"，不采集用户输入内容、不采集页面行为内容，
 * 既不触碰隐私红线，也让信号口径清晰可解释（用户被挑战时可以说明原因）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class LoginRiskSignal {

    /** 用户名 */
    private final String username;

    /** 设备标识 */
    private final String deviceId;

    /** 该设备是否已信任 */
    private final boolean deviceTrusted;

    /** 近期连续失败次数（含密码错误与挑战失败：统一计数） */
    private final int failureCount;

    /** 运行环境是否疑似自动化（由前端上报的环境自检信号 + 服务端看到的请求特征共同判断） */
    private final boolean automationSuspected;

    public LoginRiskSignal(String username, String deviceId, boolean deviceTrusted,
                           int failureCount, boolean automationSuspected) {
        this.username = username;
        this.deviceId = deviceId;
        this.deviceTrusted = deviceTrusted;
        this.failureCount = failureCount;
        this.automationSuspected = automationSuspected;
    }

    public String getUsername() {
        return username;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isDeviceTrusted() {
        return deviceTrusted;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public boolean isAutomationSuspected() {
        return automationSuspected;
    }
}
