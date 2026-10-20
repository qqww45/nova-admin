package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户持久化对象（对应 biz_customer）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_customer")
@Schema(name = "BizCustomer", description = "客户")
public class BizCustomer extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户名称（唯一，标准化后比对） */
    @Schema(description = "客户名称")
    private String customerName;

    /** 客户编号 */
    @Schema(description = "客户编号")
    private String customerCode;

    /** 地址 */
    @Schema(description = "地址")
    private String address;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 状态：1 启用 0 停用 */
    @Schema(description = "状态：1启用 0停用")
    private Integer status;
}
