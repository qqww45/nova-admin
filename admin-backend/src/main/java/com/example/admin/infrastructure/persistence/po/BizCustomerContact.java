package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户联系人持久化对象（对应 biz_customer_contact）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_customer_contact")
@Schema(name = "BizCustomerContact", description = "客户联系人")
public class BizCustomerContact extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private Long customerId;

    /** 姓名 */
    @Schema(description = "姓名")
    private String contactName;

    /** 职务 */
    @Schema(description = "职务")
    private String title;

    /** 电话 */
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
