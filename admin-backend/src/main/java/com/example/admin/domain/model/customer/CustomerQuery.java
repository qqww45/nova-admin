package com.example.admin.domain.model.customer;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户查询条件（领域查询对象）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：匹配客户名称 / 联系人姓名 / 电话 / 手机 */
    private String keyword;

    /** 客户名称（精确模糊匹配） */
    private String customerName;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 业务员 ID（数据权限：仅本人数据时由服务端注入） */
    private Long salesmanId;
}
