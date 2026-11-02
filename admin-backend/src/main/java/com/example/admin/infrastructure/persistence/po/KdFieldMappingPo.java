package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账套字段映射持久化对象（对应 kd_field_mapping）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_field_mapping")
@Schema(name = "KdFieldMappingPo", description = "账套字段映射")
public class KdFieldMappingPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "绑定ID")
    private Long bindingId;

    @Schema(description = "映射键")
    private String mappingKey;

    @Schema(description = "映射值")
    private String mappingValue;

    @Schema(description = "备注")
    private String remark;
}
