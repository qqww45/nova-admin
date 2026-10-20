package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.LoginRiskSignal;
import com.example.admin.domain.model.auth.RiskLevel;

/**
 * 登录风险评级策略（领域服务接口）。
 *
 * <p>为什么评级规则放在领域层：
 * "什么样的登录需要被验证、验证到什么强度"是业务安全规则，
 * 与用内存还是 Redis、用哪种验证码无关；放在领域层才能让规则可测试、可演进。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface LoginRiskPolicy {

    /**
     * 评估登录风险等级。
     *
     * @param signal 风险信号
     * @return 风险等级（决定后续验证强度）
     */
    RiskLevel evaluate(LoginRiskSignal signal);
}
