package com.example.admin.domain.model.customer;

import lombok.Data;

/**
 * 客户开票信息（客户聚合下的子表，需求 3.3）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class CustomerInvoice {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 开票名称 */
    private String invoiceTitle;

    /** 纳税人识别号 */
    private String taxNo;

    /** 开户行 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 开票地址 */
    private String invoiceAddress;

    /** 开票电话 */
    private String invoicePhone;

    /** 发票类型：SPECIAL=增值税专用发票 NORMAL=普通发票（字典 invoice_type） */
    private String invoiceType;

    /** 是否默认：1 是 0 否 */
    private Integer isDefault;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /**
     * 是否为默认开票信息。
     *
     * @return true 表示默认
     */
    public boolean isDefaultInvoice() {
        return isDefault != null && isDefault == 1;
    }

    /**
     * 是否启用。
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
