package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采集器回执。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdCommandAckReq", description = "指令回执")
public class KdCommandAckReq {

    @Schema(description = "是否执行成功")
    private Boolean ok;

    @Schema(description = "结果摘要")
    @Size(max = 400, message = "结果摘要过长")
    private String message;
}
