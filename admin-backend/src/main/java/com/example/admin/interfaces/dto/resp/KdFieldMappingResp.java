package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 账套字段映射响应。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdFieldMappingResp", description = "字段映射")
public class KdFieldMappingResp {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "绑定 ID")
    private String bindingId;

    @Schema(description = "映射键")
    private String mappingKey;

    @Schema(description = "映射值")
    private String mappingValue;

    @Schema(description = "备注")
    private String remark;
}
