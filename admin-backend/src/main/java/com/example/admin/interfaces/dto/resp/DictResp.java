package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典项响应（税率、币种等，禁止前端硬编码）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "DictResp", description = "字典项")
public class DictResp {

    @Schema(description = "字典编码")
    private String dictCode;

    @Schema(description = "字典值")
    private String dictValue;

    @Schema(description = "字典标签")
    private String dictLabel;

    @Schema(description = "显示排序")
    private Integer sort;
}
