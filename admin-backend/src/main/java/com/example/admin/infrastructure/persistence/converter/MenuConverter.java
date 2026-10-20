package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.menu.Menu;
import com.example.admin.infrastructure.persistence.po.SysMenu;
import org.springframework.stereotype.Component;

/**
 * 菜单 PO ↔ 领域模型 转换器。
 *
 * <p>注意：{@code children} 由菜单树领域服务在内存中构建，不参与持久化转换，
 * 避免把"运行时树形结构"误写入数据库。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class MenuConverter {

    /**
     * PO 转领域模型。
     *
     * @param po 持久化对象
     * @return 菜单实体
     */
    public Menu toDomain(SysMenu po) {
        if (po == null) {
            return null;
        }
        Menu menu = new Menu();
        BeanUtil.copyProperties(po, menu, "children");
        return menu;
    }

    /**
     * 领域模型转 PO。
     *
     * @param menu 菜单实体
     * @return 持久化对象
     */
    public SysMenu toPo(Menu menu) {
        if (menu == null) {
            return null;
        }
        SysMenu po = new SysMenu();
        BeanUtil.copyProperties(menu, po, "children");
        return po;
    }
}
