package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下发给采集器的任务清单（含该账套的字段映射与水位）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdClientTaskResp", description = "采集器任务清单")
public class KdClientTaskResp {

    @Schema(description = "实体")
    private String entity;

    @Schema(description = "金蝶 FormId")
    private String formId;

    @Schema(description = "上报接口")
    private String endpoint;

    @Schema(description = "金蝶 FieldKeys")
    private String fieldKeys;

    @Schema(description = "水位字段")
    private String watermarkField;

    @Schema(description = "起点水位（客户端从该时间之后拉取）")
    private LocalDateTime watermark;

    @Schema(description = "账套级字段映射（key → value）")
    private Map<String, String> mappings = new LinkedHashMap<>();
}
