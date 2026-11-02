package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单实体上报结果。
 *
 * <p>客户端收到 200 后才推进本地水位（at-least-once + 服务端幂等）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdSyncResultResp", description = "上报结果")
public class KdSyncResultResp {

    @Schema(description = "上行行数")
    private int received;

    @Schema(description = "落库行数")
    private int upserted;

    @Schema(description = "跳过行数")
    private int skipped;

    @Schema(description = "服务端当前水位（下次增量起点）")
    private LocalDateTime watermark;
}
