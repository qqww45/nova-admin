package com.example.admin.application.assembler;

import com.example.admin.domain.model.dept.Dept;
import com.example.admin.interfaces.dto.resp.DeptVo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门领域模型 → 响应 DTO 装配器（含子节点递归）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class DeptAssembler {

    /**
     * 领域模型转视图对象（递归）。
     *
     * @param dept 部门领域模型
     * @return 部门 VO
     */
    public DeptVo toVo(Dept dept) {
        DeptVo vo = new DeptVo();
        vo.setId(String.valueOf(dept.getId()));
        vo.setParentId(String.valueOf(dept.getParentId()));
        vo.setDeptName(dept.getDeptName());
        vo.setSort(dept.getSort());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setStatus(dept.getStatus());
        vo.setRemark(dept.getRemark());
        vo.setCreateTime(dept.getCreateTime());
        List<DeptVo> children = new ArrayList<>();
        if (dept.getChildren() != null) {
            for (Dept child : dept.getChildren()) {
                children.add(toVo(child));
            }
        }
        vo.setChildren(children);
        return vo;
    }

    /**
     * 部门列表转换。
     *
     * @param depts 部门列表
     * @return 视图对象列表
     */
    public List<DeptVo> toVoList(List<Dept> depts) {
        List<DeptVo> list = new ArrayList<>();
        if (depts == null) {
            return list;
        }
        for (Dept dept : depts) {
            list.add(toVo(dept));
        }
        return list;
    }
}
