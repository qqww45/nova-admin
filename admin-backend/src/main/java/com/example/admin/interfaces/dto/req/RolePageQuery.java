package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 角色分页查询条件。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "RolePageQuery", description = "角色分页查询条件")
public class RolePageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 角色名称（模糊匹配） */
    @Schema(description = "角色名称（模糊匹配）")
    private String roleName;

    /** 角色标识（模糊匹配） */
    @Schema(description = "角色权限字符串（模糊匹配）")
    private String roleKey;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 创建时间起 */
    @Schema(description = "创建时间起")
    private LocalDateTime beginTime;

    /** 创建时间止 */
    @Schema(description = "创建时间止")
    private LocalDateTime endTime;
}
