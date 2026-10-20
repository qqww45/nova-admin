package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色下拉选项（用于用户分配角色、搜索条件等）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RoleOptionResp", description = "角色下拉选项")
public class RoleOptionResp {

    /** 角色 ID */
    @Schema(description = "角色ID")
    private String id;

    /** 角色名称 */
    @Schema(description = "角色名称")
    private String roleName;

    /** 角色权限字符串 */
    @Schema(description = "角色权限字符串")
    private String roleKey;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态")
    private Integer status;
}
