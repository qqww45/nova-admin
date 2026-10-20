package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统配置新增/修改请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ConfigSaveReq", description = "系统配置保存请求")
public class ConfigSaveReq {

    /** 配置键（新增时必填；修改时以路径参数为准） */
    @NotBlank(message = "配置键不能为空")
    @Size(max = 100, message = "配置键长度不能超过 100")
    @Schema(description = "配置键", example = "admin.title")
    private String configKey;

    /** 配置值 */
    @Schema(description = "配置值", example = "通用后台管理系统")
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    @Schema(description = "值类型", example = "STRING")
    private String configType;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    @Schema(description = "备注")
    private String remark;
}
