package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开启同步批次请求（客户端每次同步调用一次）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdSyncBatchStartReq", description = "开启同步批次")
public class KdSyncBatchStartReq {

    @NotBlank(message = "批次号不能为空")
    @Size(max = 64, message = "批次号过长")
    @Schema(description = "客户端生成的批次号（同一批次重试时必须一致）")
    private String batchId;

    @Schema(description = "实体：customer / outbound / outbound_line / ALL")
    private String entity;

    @Schema(description = "模式：INCREMENTAL / FULL")
    private String mode;

    @Schema(description = "起点水位")
    private LocalDateTime watermarkFrom;

    @Size(max = 50)
    @Schema(description = "客户端版本")
    private String clientVersion;
}
