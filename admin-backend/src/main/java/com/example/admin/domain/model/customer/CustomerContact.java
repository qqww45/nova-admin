package com.example.admin.domain.model.customer;

import lombok.Data;

/**
 * 客户联系人（客户聚合下的子表，需求 3.2）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class CustomerContact {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 姓名 */
    private String contactName;

    /** 职务 */
    private String title;

    /** 电话 */
    private String phone;

    /** 手机 */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 是否默认：1 是 0 否 */
    private Integer isDefault;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /**
     * 是否为默认联系人。
     *
     * @return true 表示默认
     */
    public boolean isDefaultContact() {
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

    /**
     * 联系方式是否完整（电话与手机至少一个非空）。
     *
     * @return true 表示至少有一个联系方式
     */
    public boolean hasContactWay() {
        return (phone != null && !phone.isBlank()) || (mobile != null && !mobile.isBlank());
    }
}
