package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单视图对象（{@code /getRouters} 与菜单管理列表通用）。
 *
 * <p>说明：
 * <ul>
 *     <li>{@code component} 为前端 views 下的相对路径（如 {@code system/user/index}），
 *         前端通过 {@code import.meta.glob} 动态映射为真实组件；</li>
 *     <li>{@code permission} 为权限标识，按钮级菜单用于接口与按钮鉴权；</li>
 *     <li>{@code children} 用于树形展示与前端递归生成路由。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "MenuVo", description = "菜单视图对象")
public class MenuVo {

    /** 菜单 ID */
    @Schema(description = "菜单ID")
    private String id;

    /** 父菜单 ID */
    @Schema(description = "父菜单ID")
    private String parentId;

    /** 菜单名称 */
    @Schema(description = "菜单名称")
    private String menuName;

    /** 菜单类型：1 目录 2 菜单 3 按钮 */
    @Schema(description = "菜单类型：1目录 2菜单 3按钮")
    private Integer menuType;

    /** 路由地址 */
    @Schema(description = "路由地址")
    private String path;

    /** 前端组件路径 */
    @Schema(description = "前端组件路径")
    private String component;

    /** 权限标识 */
    @Schema(description = "权限标识")
    private String permission;

    /** 图标 */
    @Schema(description = "图标")
    private String icon;

    /** 排序 */
    @Schema(description = "排序")
    private Integer sort;

    /** 是否显示：0 隐藏 1 显示 */
    @Schema(description = "是否显示")
    private Integer visible;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private String createTime;

    /** 子菜单 */
    @Schema(description = "子菜单")
    private List<MenuVo> children = new ArrayList<>();
}
