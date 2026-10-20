package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门视图对象（树形）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "DeptVo", description = "部门视图对象")
public class DeptVo {

    /** 部门 ID */
    @Schema(description = "部门ID")
    private String id;

    /** 父部门 ID */
    @Schema(description = "父部门ID")
    private String parentId;

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

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 子部门 */
    @Schema(description = "子部门")
    private List<DeptVo> children = new ArrayList<>();
}
