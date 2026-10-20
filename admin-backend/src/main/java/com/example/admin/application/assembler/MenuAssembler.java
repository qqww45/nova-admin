package com.example.admin.application.assembler;

import com.example.admin.domain.model.menu.Menu;
import com.example.admin.interfaces.dto.resp.MenuVo;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单领域模型 → 响应 DTO 装配器（含子节点递归）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class MenuAssembler {

    /** 时间展示格式（避免前端二次解析） */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 领域模型转视图对象（递归转换子节点）。
     *
     * @param menu 菜单领域模型
     * @return 菜单 VO
     */
    public MenuVo toVo(Menu menu) {
        MenuVo vo = new MenuVo();
        vo.setId(String.valueOf(menu.getId()));
        vo.setParentId(String.valueOf(menu.getParentId()));
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setPermission(menu.getPermission());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        vo.setRemark(menu.getRemark());
        vo.setCreateTime(menu.getCreateTime() == null ? null : menu.getCreateTime().format(FORMATTER));
        List<MenuVo> children = new ArrayList<>();
        if (menu.getChildren() != null) {
            for (Menu child : menu.getChildren()) {
                children.add(toVo(child));
            }
        }
        vo.setChildren(children);
        return vo;
    }

    /**
     * 菜单列表转换。
     *
     * @param menus 菜单列表（通常为树根集合）
     * @return 视图对象列表
     */
    public List<MenuVo> toVoList(List<Menu> menus) {
        List<MenuVo> list = new ArrayList<>();
        if (menus == null) {
            return list;
        }
        for (Menu menu : menus) {
            list.add(toVo(menu));
        }
        return list;
    }
}
