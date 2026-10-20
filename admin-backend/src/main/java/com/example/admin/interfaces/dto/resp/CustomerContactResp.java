package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户联系人响应。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerContactResp", description = "客户联系人")
public class CustomerContactResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "姓名")
    private String contactName;

    @Schema(description = "职务")
    private String title;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "手机")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "是否默认：1是 0否")
    private Integer isDefault;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
