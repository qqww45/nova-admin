package com.example.admin.domain.model.kd;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 绑定状态。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Getter
@AllArgsConstructor
public enum KdBindingStatus {

    /** 正常：令牌可用 */
    ACTIVE("ACTIVE", "正常"),

    /** 已吊销：离职 / 换电脑，令牌立即失效，重新绑定即恢复 */
    REVOKED("REVOKED", "已吊销"),
    ;

    /** 存库编码 */
    private final String code;

    /** 界面标签 */
    private final String label;
}
