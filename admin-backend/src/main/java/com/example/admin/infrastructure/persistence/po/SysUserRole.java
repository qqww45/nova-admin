package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-角色关联实体（对应表 {@code sys_user_role}）。
 *
 * <p>采用逻辑外键（user_id / role_id），不在数据库层建立物理外键，
 * 以保证 SQLite / MySQL / PostgreSQL 之间无缝迁移。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@TableName("sys_user_role")
@Schema(name = "SysUserRole", description = "用户角色关联")
public class SysUserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    @Schema(description = "用户ID")
    private Long userId;

    /** 角色 ID */
    @Schema(description = "角色ID")
    private Long roleId;

    /** 创建者 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
