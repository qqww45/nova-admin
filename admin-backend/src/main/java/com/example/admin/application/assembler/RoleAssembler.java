package com.example.admin.application.assembler;

import com.example.admin.common.constant.Constants;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.role.Role;
import com.example.admin.interfaces.dto.resp.RoleResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色领域模型 → 响应 DTO 装配器。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class RoleAssembler {

    /**
     * 领域模型转角色响应。
     *
     * @param role 角色聚合
     * @return 角色响应 DTO
     */
    public RoleResp toResp(Role role) {
        RoleResp resp = new RoleResp();
        resp.setId(String.valueOf(role.getId()));
        resp.setRoleName(role.getRoleName());
        resp.setRoleKey(role.getRoleKey());
        resp.setSort(role.getSort());
        resp.setStatus(role.getStatus());
        resp.setRemark(role.getRemark());
        resp.setCreateTime(role.getCreateTime());
        resp.setMenuIds(role.getMenuIds().stream().map(String::valueOf).toList());
        resp.setBuiltin(Constants.SUPER_ADMIN_ROLE_KEY.equals(role.getRoleKey()));
        return resp;
    }

    /**
     * 角色列表转换。
     *
     * @param roles 角色列表
     * @return 响应列表
     */
    public List<RoleResp> toRespList(List<Role> roles) {
        List<RoleResp> list = new ArrayList<>();
        if (roles == null) {
            return list;
        }
        for (Role role : roles) {
            list.add(toResp(role));
        }
        return list;
    }

    /**
     * 分页结果转换。
     *
     * @param page 领域分页结果
     * @return DTO 分页结果
     */
    public PageResult<RoleResp> toPage(PageResult<Role> page) {
        PageResult<RoleResp> result = new PageResult<>();
        result.setRecords(toRespList(page.getRecords()));
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        return result;
    }
}
