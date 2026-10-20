package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户联系人保存请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerContactReq", description = "客户联系人保存请求")
public class CustomerContactReq {

    /** 主键（修改时必填） */
    @Schema(description = "主键，新增时为空")
    private String id;

    /** 客户 ID */
    @NotBlank(message = "客户ID不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    /** 姓名 */
    @NotBlank(message = "联系人姓名不能为空")
    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contactName;

    /** 职务 */
    @Schema(description = "职务")
    private String title;

    /** 电话（与手机至少一个非空） */
    @Schema(description = "电话")
    private String phone;

    /** 手机 */
    @Schema(description = "手机")
    private String mobile;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 是否默认：1 是 0 否 */
    @Schema(description = "是否默认：1是 0否")
    private Integer isDefault;

    /** 状态：1 启用 0 停用 */
    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
