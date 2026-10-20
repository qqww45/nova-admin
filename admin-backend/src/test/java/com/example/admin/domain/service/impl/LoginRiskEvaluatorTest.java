package com.example.admin.domain.service.impl;

import com.example.admin.domain.model.auth.LoginRiskSignal;
import com.example.admin.domain.model.auth.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风险评级单元测试：三条规则的优先级、边界阈值与空值保护。
 */
class LoginRiskEvaluatorTest {

    private final LoginRiskEvaluator evaluator = new LoginRiskEvaluator();

    private static LoginRiskSignal signal(boolean trusted, int failures, boolean automation) {
        return new LoginRiskSignal("admin", "device-1", trusted, failures, automation);
    }

    @Test
    @DisplayName("信号为 null：按轻验证处理（不因空指针崩溃，也不放行）")
    void evaluate_nullSignal_returnsLight() {
        assertThat(evaluator.evaluate(null)).isEqualTo(RiskLevel.LIGHT);
    }

    @Test
    @DisplayName("已信任设备 + 无失败 + 非自动化 → 无感放行")
    void evaluate_trustedNoFailure_returnsNone() {
        assertThat(evaluator.evaluate(signal(true, 0, false))).isEqualTo(RiskLevel.NONE);
    }

    @Test
    @DisplayName("陌生设备 + 无失败 → 轻验证")
    void evaluate_untrustedNoFailure_returnsLight() {
        assertThat(evaluator.evaluate(signal(false, 0, false))).isEqualTo(RiskLevel.LIGHT);
    }

    @Test
    @DisplayName("失败 1 次：即使设备已信任也升级为轻验证（越打越难）")
    void evaluate_oneFailureOnTrustedDevice_returnsLight() {
        assertThat(evaluator.evaluate(signal(true, 1, false))).isEqualTo(RiskLevel.LIGHT);
    }

    @Test
    @DisplayName("失败 2 次：仍是轻验证但已计入")
    void evaluate_twoFailures_returnsLight() {
        assertThat(evaluator.evaluate(signal(true, 2, false))).isEqualTo(RiskLevel.LIGHT);
    }

    @Test
    @DisplayName("失败 3 次（阈值边界）→ 强验证")
    void evaluate_threeFailures_returnsStrong() {
        assertThat(evaluator.evaluate(signal(true, 3, false))).isEqualTo(RiskLevel.STRONG);
    }

    @Test
    @DisplayName("失败超过 3 次 → 强验证")
    void evaluate_manyFailures_returnsStrong() {
        assertThat(evaluator.evaluate(signal(true, 10, false))).isEqualTo(RiskLevel.STRONG);
    }

    @Test
    @DisplayName("自动化特征优先级最高：已信任设备 + 无失败也走强验证")
    void evaluate_automationOverridesTrust() {
        assertThat(evaluator.evaluate(signal(true, 0, true))).isEqualTo(RiskLevel.STRONG);
    }

    @Test
    @DisplayName("自动化 + 失败并存：仍是强验证（不降级）")
    void evaluate_automationAndFailures_returnsStrong() {
        assertThat(evaluator.evaluate(signal(false, 5, true))).isEqualTo(RiskLevel.STRONG);
    }
}
