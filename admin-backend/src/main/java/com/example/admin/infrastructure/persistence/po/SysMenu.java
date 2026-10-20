package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 菜单/权限实体（对应表 {@code sys_menu}）。
 *
 * <p>三级模型：
 * <ul>
 *     <li>目录（menu_type=1）：仅有路由容器作用，如“系统管理”；</li>
 *     <li>菜单（menu_type=2）：对应前端页面，component 指向 views 下的文件路径；</li>
 *     <li>按钮（menu_type=3）：无页面，permission 作为接口与按钮的权限标识。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
@Schema(name = "SysMenu", description = "菜单权限")
public class SysMenu extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 父菜单 ID，顶级为 0 */
    @Schema(description = "父菜单ID")
    private Long parentId;

    /** 菜单名称 */
    @Schema(description = "菜单名称")
    private String menuName;

    /** 菜单类型：1 目录 2 菜单 3 按钮 */
    @Schema(description = "菜单类型：1目录 2菜单 3按钮")
    private Integer menuType;

    /** 路由地址 */
    @Schema(description = "路由地址")
    private String path;

    /** 前端组件路径（相对 views 目录，如 system/user/index） */
    @Schema(description = "前端组件路径")
    private String component;

    /** 权限标识（如 system:user:add） */
    @Schema(description = "权限标识")
    private String permission;

    /** 菜单图标（Iconify 图标名，如 ep:user） */
    @Schema(description = "菜单图标")
    private String icon;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer sort;

    /** 是否显示：0 隐藏 1 显示 */
    @Schema(description = "是否显示：0隐藏 1显示")
    private Integer visible;

    /** 菜单状态：0 正常 1 停用 */
    @Schema(description = "菜单状态：0正常 1停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 子菜单（非数据库字段，树形结构用） */
    @Schema(description = "子菜单（非数据库字段）")
    @TableField(exist = false)
    private List<SysMenu> children;
}
