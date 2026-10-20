package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.user.User;
import com.example.admin.infrastructure.persistence.po.SysUser;
import org.springframework.stereotype.Component;

/**
 * 用户 PO ↔ 领域模型 转换器（防腐层 ACL）。
 *
 * <p>作用：阻断持久化对象对领域模型的侵入。
 * 若没有这层，领域模型就必须带上 {@code @TableName}、{@code @TableLogic} 等 ORM 注解，
 * 领域层将被技术细节污染（DDD 中称为"数据库驱动设计"，是需要避免的）。
 *
 * <p>字段映射注意：PO 的 {@code password} 对应领域模型的 {@code passwordHash}，
 * 命名差异正是为了在代码层面强制区分"存储的哈希"与"用户输入的明文"。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class UserConverter {

    /**
     * PO 转领域模型（不填充角色，由仓储单独加载）。
     *
     * @param po 持久化对象
     * @return 用户聚合根；入参为 null 返回 null
     */
    public User toDomain(SysUser po) {
        if (po == null) {
            return null;
        }
        User user = new User();
        BeanUtil.copyProperties(po, user, "password");
        user.setPasswordHash(po.getPassword());
        return user;
    }

    /**
     * 领域模型转 PO。
     *
     * @param user 用户聚合根
     * @return 持久化对象；入参为 null 返回 null
     */
    public SysUser toPo(User user) {
        if (user == null) {
            return null;
        }
        SysUser po = new SysUser();
        BeanUtil.copyProperties(user, po, "passwordHash", "roleIds");
        po.setPassword(user.getPasswordHash());
        return po;
    }
}
