package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.dept.Dept;
import com.example.admin.infrastructure.persistence.po.SysDept;
import org.springframework.stereotype.Component;

/**
 * 部门 PO ↔ 领域模型 转换器。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class DeptConverter {

    /**
     * PO 转领域模型。
     *
     * @param po 持久化对象
     * @return 部门聚合根
     */
    public Dept toDomain(SysDept po) {
        if (po == null) {
            return null;
        }
        Dept dept = new Dept();
        BeanUtil.copyProperties(po, dept, "children");
        return dept;
    }

    /**
     * 领域模型转 PO。
     *
     * @param dept 部门聚合根
     * @return 持久化对象
     */
    public SysDept toPo(Dept dept) {
        if (dept == null) {
            return null;
        }
        SysDept po = new SysDept();
        BeanUtil.copyProperties(dept, po, "children");
        return po;
    }
}
