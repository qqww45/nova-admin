package com.example.admin.domain.model.menu;

import com.example.admin.common.constant.Constants;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单实体（目录 / 菜单 / 按钮三级，同时承载权限标识）。
 *
 * <p>设计说明：菜单既是"前端路由数据源"，也是"权限定义的载体"。
 * 按钮级菜单的 permission 字段即为接口与按钮鉴权标识，
 * 因此菜单树的构建规则（哪些节点可出现在路由中）是领域规则，
 * 放在本类与 {@code MenuTreeDomainService} 中，而非 Controller 里。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class Menu {

    /** 主键 */
    private Long id;

    /** 父菜单 ID（顶级为 0） */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 菜单类型：1 目录 2 菜单 3 按钮 */
    private Integer menuType;

    /** 路由地址 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 权限标识（按钮级菜单使用） */
    private String permission;

    /** 图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 是否显示：0 隐藏 1 显示 */
    private Integer visible;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 子菜单（树形结构） */
    private List<Menu> children = new ArrayList<>();

    /**
     * 是否为目录。
     *
     * @return 是否目录
     */
    public boolean isDirectory() {
        return Constants.MENU_TYPE_DIR.equals(this.menuType);
    }

    /**
     * 是否为页面菜单。
     *
     * @return 是否页面菜单
     */
    public boolean isMenu() {
        return Constants.MENU_TYPE_MENU.equals(this.menuType);
    }

    /**
     * 是否为按钮（按钮不参与路由生成，只提供权限标识）。
     *
     * @return 是否按钮
     */
    public boolean isButton() {
        return Constants.MENU_TYPE_BUTTON.equals(this.menuType);
    }

    /**
     * 是否可作为前端路由（启用 + 可见 + 目录/菜单类型）。
     *
     * @return 是否可路由
     */
    public boolean isRoutable() {
        return (isDirectory() || isMenu())
                && Constants.STATUS_NORMAL.equals(this.status)
                && Integer.valueOf(1).equals(this.visible);
    }

    /**
     * 挂载子菜单。
     *
     * @param child 子菜单
     */
    public void addChild(Menu child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
