package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增/修改请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "DeptSaveReq", description = "部门新增/修改请求")
public class DeptSaveReq {

    /** 部门 ID（新增时为空） */
    @Schema(description = "部门ID，新增时为空")
    private String id;

    /** 父部门 ID */
    @Schema(description = "父部门ID，顶级为 0")
    private String parentId;

    /** 部门名称 */
    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称长度不能超过 64")
    private String deptName;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer sort;

    /** 负责人 */
    @Schema(description = "负责人")
    @Size(max = 64, message = "负责人名称过长")
    private String leader;

    /** 联系电话 */
    @Schema(description = "联系电话")
    @Pattern(regexp = "^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})?$", message = "联系电话格式不正确")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
