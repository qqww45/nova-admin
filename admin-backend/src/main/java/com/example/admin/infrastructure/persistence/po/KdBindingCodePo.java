package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 一次性绑定码持久化对象（对应 kd_binding_code）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_binding_code")
@Schema(name = "KdBindingCodePo", description = "一次性绑定码")
public class KdBindingCodePo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "生成人")
    private Long userId;

    @Schema(description = "绑定码摘要")
    private String codeHash;

    @Schema(description = "过期时间")
    private LocalDateTime expireAt;

    @Schema(description = "使用时间")
    private LocalDateTime usedAt;

    @Schema(description = "备注")
    private String remark;
}
