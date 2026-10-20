package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入字段映射持久化对象（对应 biz_import_mapping）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_import_mapping")
@Schema(name = "BizImportMapping", description = "导入字段映射")
public class BizImportMapping extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模块：CUSTOMER / ORDER / SHIPMENT */
    @Schema(description = "模块")
    private String module;

    /** 系统字段名 */
    @Schema(description = "系统字段名")
    private String fieldName;

    /** 表头别名（英文逗号分隔） */
    @Schema(description = "表头别名")
    private String aliases;

    /** 是否必填：1 是 0 否 */
    @Schema(description = "是否必填：1是 0否")
    private Integer required;

    /** 模板列序 */
    @Schema(description = "模板列序")
    private Integer columnOrder;

    /** 缺省默认值 */
    @Schema(description = "缺省默认值")
    private String defaultValue;

    /** 值类型：STRING / NUMBER / DATE / RATE */
    @Schema(description = "值类型")
    private String fieldType;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
