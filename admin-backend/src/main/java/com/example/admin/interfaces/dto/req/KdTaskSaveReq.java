package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 同步任务保存请求。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdTaskSaveReq", description = "同步任务保存")
public class KdTaskSaveReq {

    @Schema(description = "主键；为空表示新增")
    private String id;

    @Size(max = 64)
    @Schema(description = "账套：* 表示全局模板")
    private String acctId;

    @NotBlank(message = "实体不能为空")
    @Size(max = 32)
    @Schema(description = "实体：customer / outbound / outbound_line")
    private String entity;

    @NotBlank(message = "FormId 不能为空")
    @Size(max = 64)
    @Schema(description = "金蝶 FormId")
    private String formId;

    @NotBlank(message = "上报接口不能为空")
    @Size(max = 200)
    @Schema(description = "上报接口")
    private String endpoint;

    @NotBlank(message = "字段集不能为空")
    @Size(max = 2000)
    @Schema(description = "金蝶 FieldKeys")
    private String fieldKeys;

    @Size(max = 64)
    @Schema(description = "水位字段")
    private String watermarkField;

    @Schema(description = "是否启用")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sortNo;

    @Size(max = 500)
    @Schema(description = "备注")
    private String remark;
}
