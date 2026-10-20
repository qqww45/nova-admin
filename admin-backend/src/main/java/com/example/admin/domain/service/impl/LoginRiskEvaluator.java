package com.example.admin.domain.service.impl;

import com.example.admin.domain.model.auth.LoginRiskSignal;
import com.example.admin.domain.model.auth.RiskLevel;
import com.example.admin.domain.service.LoginRiskPolicy;
import org.springframework.stereotype.Component;

/**
 * 风险评级实现（规则引擎的最小可用版本）。
 *
 * <p>三条规则，按优先级：
 * <ol>
 *     <li><b>自动化特征 → 强验证</b>：运行环境暴露脚本痕迹，直接要求第二因子；</li>
 *     <li><b>失败序列 → 越打越难</b>：近期失败越多等级越高，攻击者的每次尝试都抬高自己的成本；</li>
 *     <li><b>设备信任 → 无感</b>：已信任设备且无异常信号时直接放行。</li>
 * </ol>
 *
 * <p>为什么"陌生设备"是轻验证而不是无感：
 * 设备信任是强信号，但一次全新的设备没有任何历史，至少要确认"操作者是本人"。
 * 好在成本极低（一次按压，约 1 秒，无需输入任何文字），且通过后即可选择信任此设备。
 *
 * <p>阈值集中在此处，后续接入更多信号（地理位置、行为基线）时只改这一个文件。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class LoginRiskEvaluator implements LoginRiskPolicy {

    /** 失败次数达到此值 → 强验证 */
    private static final int STRONG_FAILURE_THRESHOLD = 3;

    @Override
    public RiskLevel evaluate(LoginRiskSignal signal) {
        if (signal == null) {
            return RiskLevel.LIGHT;
        }
        // 1. 自动化特征：最高优先级
        if (signal.isAutomationSuspected()) {
            return RiskLevel.STRONG;
        }
        // 2. 失败序列：越打越难
        if (signal.getFailureCount() >= STRONG_FAILURE_THRESHOLD) {
            return RiskLevel.STRONG;
        }
        if (signal.getFailureCount() > 0) {
            return RiskLevel.LIGHT;
        }
        // 3. 设备信任：无异常则无感
        if (signal.isDeviceTrusted()) {
            return RiskLevel.NONE;
        }
        // 4. 陌生设备：轻验证
        return RiskLevel.LIGHT;
    }
}
