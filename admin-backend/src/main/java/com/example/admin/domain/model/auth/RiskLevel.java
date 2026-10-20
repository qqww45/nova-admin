package com.example.admin.domain.model.auth;

/**
 * 登录风险等级（决定验证强度）。
 *
 * <p>为什么分级而不是"人人过一道验证码"：
 * 验证的成本应该由可疑请求承担。绝大多数正常登录毫无风险，
 * 让所有用户每次都做一道题，是安全向体验的无谓转嫁；
 * 分级之后，防护资源集中在少数异常请求上，既更轻也更强。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public enum RiskLevel {

    /** 无风险：直接放行，用户零操作 */
    NONE(0, "无感通过"),

    /** 中风险：轻验证（一次按压/滑动，无文字输入） */
    LIGHT(1, "轻验证"),

    /** 高风险：强验证（一次性码等第二因子） */
    STRONG(2, "强验证");

    private final int value;

    private final String label;

    RiskLevel(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
