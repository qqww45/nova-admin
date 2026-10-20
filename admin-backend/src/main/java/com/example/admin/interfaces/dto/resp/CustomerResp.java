package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户列表项响应。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerResp", description = "客户列表项")
public class CustomerResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "客户编号")
    private String customerCode;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "状态标签")
    private String statusLabel;

    @Schema(description = "默认联系人姓名")
    private String defaultContactName;

    @Schema(description = "默认联系人电话")
    private String defaultContactPhone;

    @Schema(description = "关联订单数")
    private Integer orderCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
