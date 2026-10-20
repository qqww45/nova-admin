package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户分页查询条件。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "CustomerPageQuery", description = "客户分页查询条件")
public class CustomerPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：客户名称 / 编号 / 地址 / 联系人 / 电话 */
    @Schema(description = "关键字：客户名称、编号、地址、联系人、电话")
    private String keyword;

    /** 客户名称 */
    @Schema(description = "客户名称")
    private String customerName;

    /** 状态：1 启用 0 停用 */
    @Schema(description = "状态：1启用 0停用")
    private Integer status;
}
