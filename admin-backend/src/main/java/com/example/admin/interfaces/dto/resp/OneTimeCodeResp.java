package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次性验证码下发结果。
 *
 * <p>{@code devCode} 仅在开发模式回传（便于本地自测），生产必须关闭 ——
 * 否则验证码通道形同虚设，任何人都能直接从响应里读到码。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "OneTimeCodeResp", description = "一次性验证码下发结果")
public class OneTimeCodeResp {

    /** 票据 ID（轻验证降级为一次性码时会换发新票据，前端需用新票据提交） */
    @Schema(description = "票据ID（降级换发后以新票据为准）")
    private String ticketId;

    /** 有效期（秒） */
    @Schema(description = "有效期（秒）", example = "300")
    private Long expireSeconds;

    /** 发送目标（脱敏，如 a***@example.com） */
    @Schema(description = "发送目标（脱敏）")
    private String target;

    /** 开发模式回传的验证码（生产为空） */
    @Schema(description = "开发模式下的验证码（生产为空）")
    private String devCode;
}
