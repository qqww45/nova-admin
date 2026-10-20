package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 部门实体（对应表 {@code sys_dept}）。
 *
 * <p>采用 parentId 自关联构建树形结构，与用户为逻辑外键关系（不使用数据库外键约束）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
@Schema(name = "SysDept", description = "部门")
public class SysDept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 父部门 ID，顶级为 0 */
    @Schema(description = "父部门ID")
    private Long parentId;

    /** 部门名称 */
    @Schema(description = "部门名称")
    private String deptName;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer sort;

    /** 负责人 */
    @Schema(description = "负责人")
    private String leader;

    /** 联系电话 */
    @Schema(description = "联系电话")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 部门状态：0 正常 1 停用 */
    @Schema(description = "部门状态：0正常 1停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 子部门（非数据库字段） */
    @Schema(description = "子部门（非数据库字段）")
    @TableField(exist = false)
    private List<SysDept> children;
}
