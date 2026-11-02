package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账套字段映射保存请求。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdFieldMappingSaveReq", description = "字段映射保存")
public class KdFieldMappingSaveReq {

    @NotBlank(message = "绑定不能为空")
    @Schema(description = "绑定 ID")
    private String bindingId;

    @NotBlank(message = "映射键不能为空")
    @Size(max = 64)
    @Schema(description = "映射键，如 line_field_keys / customer_po_field")
    private String mappingKey;

    @Size(max = 2000)
    @Schema(description = "映射值")
    private String mappingValue;

    @Size(max = 500)
    @Schema(description = "备注")
    private String remark;
}
