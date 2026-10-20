package com.example.admin.domain.model.customer;

import com.example.admin.domain.service.HeaderNormalizer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户聚合根（需求 3.1）。
 *
 * <p>客户是下单方主数据，唯一键为「标准化后的客户名称」。标准化口径见
 * {@link #normalizeName(String)}：去空格 + 全半角统一 + 转小写，
 * 这样"华为技术有限公司"与"华为技术有限公司 "（尾部空格）不会建成两个客户。
 *
 * <p>联系人与开票信息作为聚合内子表一并维护，保证"有且仅有一个默认"的不变量。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class Customer {

    /** 主键 */
    private Long id;

    /** 客户名称（唯一，标准化后比对） */
    private String customerName;

    /** 客户编号（[待确认]暂不需要，预留） */
    private String customerCode;

    /** 地址 */
    private String address;

    /** 备注 */
    private String remark;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 联系人列表（子表） */
    private List<CustomerContact> contacts = new ArrayList<>();

    /** 开票信息列表（子表） */
    private List<CustomerInvoice> invoices = new ArrayList<>();

    /** 关联订单数（列表展示用，非库字段） */
    private Integer orderCount;

    /**
     * 客户名称标准化（唯一键比对口径）。
     *
     * @param name 原始名称
     * @return 标准化结果（去空格 + 全半角统一 + 小写）
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return HeaderNormalizer.toHalfWidth(name).replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 是否启用。
     *
     * @return 状态为 1 返回 true
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /**
     * 取默认联系人（没有则返回 null）。
     *
     * @return 默认联系人
     */
    public CustomerContact defaultContact() {
        if (contacts == null) {
            return null;
        }
        return contacts.stream().filter(CustomerContact::isDefaultContact).findFirst().orElse(null);
    }

    /**
     * 取默认开票信息（没有则返回 null）。
     *
     * @return 默认开票信息
     */
    public CustomerInvoice defaultInvoice() {
        if (invoices == null) {
            return null;
        }
        return invoices.stream().filter(CustomerInvoice::isDefaultInvoice).findFirst().orElse(null);
    }

    /**
     * 设为默认联系人（同时取消其它联系人的默认标记，保证"有且仅有一个"）。
     *
     * @param contactId 目标联系人 ID
     */
    public void setDefaultContact(Long contactId) {
        if (contacts == null) {
            return;
        }
        for (CustomerContact contact : contacts) {
            contact.setIsDefault(contact.getId() != null && contact.getId().equals(contactId) ? 1 : 0);
        }
    }

    /**
     * 设为默认开票信息（规则同联系人）。
     *
     * @param invoiceId 目标开票信息 ID
     */
    public void setDefaultInvoice(Long invoiceId) {
        if (invoices == null) {
            return;
        }
        for (CustomerInvoice invoice : invoices) {
            invoice.setIsDefault(invoice.getId() != null && invoice.getId().equals(invoiceId) ? 1 : 0);
        }
    }
}
