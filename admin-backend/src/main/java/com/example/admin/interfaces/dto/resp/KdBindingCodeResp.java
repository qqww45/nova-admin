package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 绑定码响应。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdBindingCodeResp", description = "一次性绑定码")
public class KdBindingCodeResp {

    @Schema(description = "绑定码（仅本次返回）")
    private String code;

    @Schema(description = "过期时间")
    private LocalDateTime expireAt;

    @Schema(description = "有效秒数")
    private long expireSeconds;
}
