package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 下发指令请求。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdCommandSendReq", description = "下发指令")
public class KdCommandSendReq {

    @Schema(description = "指令名：sync.now / config.reload / token.revoke / client.update",
            example = "sync.now")
    @NotBlank(message = "指令不能为空")
    @Size(max = 32, message = "指令名过长")
    private String command;

    @Schema(description = "指令参数（JSON 字符串，可空）")
    private String payload;
}
