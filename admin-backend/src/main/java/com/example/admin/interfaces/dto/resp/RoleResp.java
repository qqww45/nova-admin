package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图对象。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "RoleResp", description = "角色视图对象")
public class RoleResp {

    /** 角色 ID */
    @Schema(description = "角色ID")
    private String id;

    /** 角色名称 */
    @Schema(description = "角色名称")
    private String roleName;

    /** 角色权限字符串 */
    @Schema(description = "角色权限字符串")
    private String roleKey;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer sort;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 菜单 ID 集合（编辑时回显权限树） */
    @Schema(description = "菜单ID集合")
    private List<String> menuIds;

    /** 是否为内置角色（内置角色不可删除） */
    @Schema(description = "是否内置角色")
    private Boolean builtin;
}
