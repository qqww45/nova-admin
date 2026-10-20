package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户开票信息保存请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerInvoiceReq", description = "客户开票信息保存请求")
public class CustomerInvoiceReq {

    /** 主键（修改时必填） */
    @Schema(description = "主键，新增时为空")
    private String id;

    /** 客户 ID */
    @NotBlank(message = "客户ID不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    /** 开票名称 */
    @NotBlank(message = "开票名称不能为空")
    @Schema(description = "开票名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String invoiceTitle;

    /** 纳税人识别号 */
    @Schema(description = "纳税人识别号")
    private String taxNo;

    /** 开户行 */
    @Schema(description = "开户行")
    private String bankName;

    /** 银行账号 */
    @Schema(description = "银行账号")
    private String bankAccount;

    /** 开票地址 */
    @Schema(description = "开票地址")
    private String invoiceAddress;

    /** 开票电话 */
    @Schema(description = "开票电话")
    private String invoicePhone;

    /** 发票类型：SPECIAL / NORMAL */
    @Schema(description = "发票类型：SPECIAL专用发票 NORMAL普通发票")
    private String invoiceType;

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
