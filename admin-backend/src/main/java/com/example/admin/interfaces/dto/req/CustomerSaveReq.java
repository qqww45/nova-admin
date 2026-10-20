package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户新增 / 修改请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerSaveReq", description = "客户保存请求")
public class CustomerSaveReq {

    /** 主键（修改时必填） */
    @Schema(description = "主键，新增时为空")
    private String id;

    /** 客户名称（唯一，标准化后比对） */
    @NotBlank(message = "客户名称不能为空")
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerName;

    /** 客户编号 */
    @Schema(description = "客户编号")
    private String customerCode;

    /** 地址 */
    @Schema(description = "地址")
    private String address;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 状态：1 启用 0 停用 */
    @Schema(description = "状态：1启用 0停用")
    private Integer status;
}
