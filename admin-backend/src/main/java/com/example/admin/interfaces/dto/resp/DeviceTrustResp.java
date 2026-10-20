package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 可信设备响应（设备管理列表项）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DeviceTrustResp", description = "可信设备")
public class DeviceTrustResp {

    /** 设备标识 */
    @Schema(description = "设备标识")
    private String deviceId;

    /** 建立信任的时间 */
    @Schema(description = "信任时间")
    private LocalDateTime trustTime;

    /** 信任到期时间 */
    @Schema(description = "到期时间")
    private LocalDateTime expireTime;

    /** 该设备当前是否在线（有活跃会话） */
    @Schema(description = "是否在线", example = "true")
    private Boolean online;
}
