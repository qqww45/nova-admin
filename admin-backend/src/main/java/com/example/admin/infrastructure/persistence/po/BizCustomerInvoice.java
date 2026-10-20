package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户开票信息持久化对象（对应 biz_customer_invoice）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_customer_invoice")
@Schema(name = "BizCustomerInvoice", description = "客户开票信息")
public class BizCustomerInvoice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private Long customerId;

    /** 开票名称 */
    @Schema(description = "开票名称")
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
    @Schema(description = "发票类型：SPECIAL=专用发票 NORMAL=普通发票")
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
