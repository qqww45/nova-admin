package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指令响应（网页与采集器共用）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdCommandResp", description = "采集器指令")
public class KdCommandResp {

    @Schema(description = "指令 ID")
    private String id;

    @Schema(description = "绑定 ID")
    private String bindingId;

    @Schema(description = "指令名")
    private String command;

    @Schema(description = "指令参数")
    private String payload;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "执行结果")
    private String result;

    @Schema(description = "下发时间")
    private LocalDateTime createTime;

    @Schema(description = "取走时间")
    private LocalDateTime deliverTime;

    @Schema(description = "完成时间")
    private LocalDateTime doneTime;
}
