package com.example.admin.domain.model.kd;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步模式。
 *
 * <p>全量模式用于"金蝶侧物理删除"兜底：批次结束后，本批次未见到的单据
 * 由服务端标记失效（{@code invalid = 1}），不做物理删除。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Getter
@AllArgsConstructor
public enum KdSyncMode {

    /** 增量（首次同步也走增量：客户端传空水位即全量拉取） */
    INCREMENTAL("INCREMENTAL", "增量"),

    /** 全量：服务端按批次差集标记失效 */
    FULL("FULL", "全量"),
    ;

    /** 存库编码 */
    private final String code;

    /** 界面标签 */
    private final String label;

    /**
     * 按存库编码解析。
     *
     * @param code 编码
     * @return 枚举；空或未匹配返回 {@link #INCREMENTAL}
     */
    public static KdSyncMode ofCode(String code) {
        for (KdSyncMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return INCREMENTAL;
    }
}
