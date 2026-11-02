package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 同步任务持久化对象（对应 kd_sync_task）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_sync_task")
@Schema(name = "KdSyncTaskPo", description = "同步任务")
public class KdSyncTaskPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账套：* 全局")
    private String acctId;

    @Schema(description = "实体标识")
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
}
