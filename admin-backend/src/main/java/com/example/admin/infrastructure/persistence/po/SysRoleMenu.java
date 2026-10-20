package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色-菜单关联实体（对应表 {@code sys_role_menu}），即角色的权限集合。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@TableName("sys_role_menu")
@Schema(name = "SysRoleMenu", description = "角色菜单关联")
public class SysRoleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色 ID */
    @Schema(description = "角色ID")
    private Long roleId;

    /** 菜单 ID */
    @Schema(description = "菜单ID")
    private Long menuId;

    /** 创建者 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
