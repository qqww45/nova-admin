package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.role.Role;
import com.example.admin.infrastructure.persistence.po.SysRole;
import org.springframework.stereotype.Component;

/**
 * 角色 PO ↔ 领域模型 转换器。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class RoleConverter {

    /**
     * PO 转领域模型（不填充菜单权限，由仓储单独加载）。
     *
     * @param po 持久化对象
     * @return 角色聚合根
     */
    public Role toDomain(SysRole po) {
        if (po == null) {
            return null;
        }
        Role role = new Role();
        BeanUtil.copyProperties(po, role);
        return role;
    }

    /**
     * 领域模型转 PO。
     *
     * @param role 角色聚合根
     * @return 持久化对象
     */
    public SysRole toPo(Role role) {
        if (role == null) {
            return null;
        }
        SysRole po = new SysRole();
        BeanUtil.copyProperties(role, po, "menuIds");
        return po;
    }
}
