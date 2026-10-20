package com.example.admin.domain.model.role;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 角色查询条件（领域查询对象）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 角色名称（模糊） */
    private String roleName;

    /** 角色权限字符串（模糊） */
    private String roleKey;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 创建时间起 */
    private LocalDateTime beginTime;

    /** 创建时间止 */
    private LocalDateTime endTime;
}
