package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次性绑定码（nova → 客户端）。
 *
 * <p>生成后 5 分钟有效，用后即焚；库里只存摘要，防止拿到数据库就能冒领令牌。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdBindingCode {

    /** 主键 */
    private Long id;

    /** 生成人 */
    private Long userId;

    /** 绑定码 SHA-256 */
    private String codeHash;

    /** 过期时间 */
    private LocalDateTime expireAt;

    /** 使用时间（非空表示已用） */
    private LocalDateTime usedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 是否可用。
     *
     * @param now 当前时间
     * @return 未使用且未过期
     */
    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expireAt != null && expireAt.isAfter(now);
    }
}
