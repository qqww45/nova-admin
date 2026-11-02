package com.example.admin.domain.model.kd;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步批次状态。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Getter
@AllArgsConstructor
public enum KdBatchStatus {

    /** 进行中：已建批次，尚未回报结果 */
    RUNNING("RUNNING", "进行中"),

    /** 成功 */
    SUCCESS("SUCCESS", "成功"),

    /** 失败：客户端回报错误，保留现场用于排障 */
    FAILED("FAILED", "失败"),
    ;

    /** 存库编码 */
    private final String code;

    /** 界面标签 */
    private final String label;
}
