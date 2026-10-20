package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色分配菜单权限请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "AssignMenuReq", description = "角色分配菜单请求")
public class AssignMenuReq {

    /** 角色 ID */
    @Schema(description = "角色ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色ID不能为空")
    private String roleId;

    /** 菜单 ID 集合（为空表示清空该角色权限） */
    @Schema(description = "菜单ID集合", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "菜单ID集合不能为空")
    private List<String> menuIds;
}
