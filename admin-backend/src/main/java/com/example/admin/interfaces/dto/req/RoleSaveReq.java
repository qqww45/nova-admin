package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色新增/修改请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "RoleSaveReq", description = "角色新增/修改请求")
public class RoleSaveReq {

    /** 角色 ID（新增时为空） */
    @Schema(description = "角色ID，新增时为空")
    private String id;

    /** 角色名称 */
    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64")
    private String roleName;

    /** 角色权限字符串（唯一标识） */
    @Schema(description = "角色权限字符串", requiredMode = Schema.RequiredMode.REQUIRED, example = "common")
    @NotBlank(message = "角色权限字符串不能为空")
    @Size(max = 64, message = "角色权限字符串长度不能超过 64")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "角色标识只能包含字母、数字、下划线，且以字母开头")
    private String roleKey;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer sort;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 菜单 ID 集合（分配权限） */
    @Schema(description = "菜单ID集合")
    private List<String> menuIds;
}
