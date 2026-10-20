package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 系统角色实体（对应表 {@code sys_role}）。
 *
 * <p>{@code roleKey} 是角色在权限表达式中的唯一标识（如 {@code admin}），
 * Spring Security 的 {@code hasRole('admin')} 即基于该字段。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
@Schema(name = "SysRole", description = "系统角色")
public class SysRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色名称 */
    @Schema(description = "角色名称")
    private String roleName;

    /** 角色权限字符串（唯一，如 admin / common / user） */
    @Schema(description = "角色权限字符串")
    private String roleKey;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer sort;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /**
     * 数据权限范围：1=全部 2=本部门 3=本部门及下级 4=仅本人 5=自定义
     * 详见领域枚举 {@code com.example.admin.domain.model.role.DataScope}
     */
    @Schema(description = "数据权限范围")
    private Integer dataScope;

    /** 菜单 ID 集合（非数据库字段，用于角色分配权限） */
    @Schema(description = "菜单ID集合（非数据库字段）")
    @TableField(exist = false)
    private List<Long> menuIds;
}
