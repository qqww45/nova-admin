package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置响应。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@Schema(name = "ConfigResp", description = "系统配置")
public class ConfigResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "值类型")
    private String configType;

    @Schema(description = "是否内置：true 不可删除")
    private Boolean builtin;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
