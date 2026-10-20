package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单新增/修改请求。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "MenuSaveReq", description = "菜单新增/修改请求")
public class MenuSaveReq {

    /** 菜单 ID（新增时为空） */
    @Schema(description = "菜单ID，新增时为空")
    private String id;

    /** 父菜单 ID */
    @Schema(description = "父菜单ID，顶级为 0")
    private String parentId;

    /** 菜单类型：1 目录 2 菜单 3 按钮 */
    @Schema(description = "菜单类型：1目录 2菜单 3按钮", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer menuType;

    /** 菜单名称 */
    @Schema(description = "菜单名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过 64")
    private String menuName;

    /** 路由地址 */
    @Schema(description = "路由地址")
    @Size(max = 200, message = "路由地址过长")
    private String path;

    /** 前端组件路径 */
    @Schema(description = "前端组件路径，如 system/user/index")
    @Size(max = 255, message = "组件路径过长")
    private String component;

    /** 权限标识 */
    @Schema(description = "权限标识，如 system:user:add")
    @Size(max = 100, message = "权限标识过长")
    @Pattern(regexp = "^[a-zA-Z0-9:*_-]*$", message = "权限标识只能包含字母、数字、冒号、星号、下划线、短横线")
    private String permission;

    /** 图标（Iconify 名称，如 ep:user） */
    @Schema(description = "图标")
    @Size(max = 100, message = "图标名称过长")
    private String icon;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer sort;

    /** 是否显示：0 隐藏 1 显示 */
    @Schema(description = "是否显示：0隐藏 1显示")
    private Integer visible;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
