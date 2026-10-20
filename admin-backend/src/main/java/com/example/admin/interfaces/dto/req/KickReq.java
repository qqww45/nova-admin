package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 强制下线请求。
 *
 * <p>deviceId 为空表示"该用户所有设备全部下线"（封号、账号被盗等场景）；
 * 不为空则只踢指定的那一台（用户发现某台设备可疑时使用，避免误伤自己的其他设备）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "KickReq", description = "强制下线请求")
public class KickReq {

    /** 用户 ID */
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /** 设备标识（为空表示全部设备） */
    @Schema(description = "设备标识（为空表示全部设备）")
    private String deviceId;
}
