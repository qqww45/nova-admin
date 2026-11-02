package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务（管理页面 + 客户端下发共用）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdSyncTaskResp", description = "同步任务")
public class KdSyncTaskResp {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "账套：* 表示全局模板")
    private String acctId;

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

    @Schema(description = "是否启用")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sortNo;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
