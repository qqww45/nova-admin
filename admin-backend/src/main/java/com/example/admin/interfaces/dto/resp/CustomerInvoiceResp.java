package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户开票信息响应。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerInvoiceResp", description = "客户开票信息")
public class CustomerInvoiceResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "开票名称")
    private String invoiceTitle;

    @Schema(description = "纳税人识别号")
    private String taxNo;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "银行账号")
    private String bankAccount;

    @Schema(description = "开票地址")
    private String invoiceAddress;

    @Schema(description = "开票电话")
    private String invoicePhone;

    @Schema(description = "发票类型")
    private String invoiceType;

    @Schema(description = "发票类型标签")
    private String invoiceTypeLabel;

    @Schema(description = "是否默认：1是 0否")
    private Integer isDefault;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
