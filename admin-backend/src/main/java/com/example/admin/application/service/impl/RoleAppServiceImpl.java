package com.example.admin.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.application.assembler.RoleAssembler;
import com.example.admin.application.assembler.UserAssembler;
import com.example.admin.application.service.RoleAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.menu.Menu;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.role.RoleQuery;
import com.example.admin.domain.repository.MenuRepository;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.port.LoginUserCache;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.interfaces.dto.req.AssignMenuReq;
import com.example.admin.interfaces.dto.req.RolePageQuery;
import com.example.admin.interfaces.dto.req.RoleSaveReq;
import com.example.admin.interfaces.dto.req.UpdateStatusReq;
import com.example.admin.interfaces.dto.resp.RoleOptionResp;
import com.example.admin.interfaces.dto.resp.RoleResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

/**
 * 角色应用服务实现。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAppServiceImpl implements RoleAppService {

    private final RoleRepository roleRepository;

    private final MenuRepository menuRepository;

    private final RoleAssembler roleAssembler;

    private final OperatorContext operatorContext;

    private final LoginUserCache loginUserCache;

    private final UserAssembler userAssembler;

    /**
     * 分页查询角色。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<RoleResp> pageRoles(RolePageQuery query) {
        RoleQuery domainQuery = new RoleQuery();
        domainQuery.setPageNum(query.getPageNum());
        domainQuery.setPageSize(query.getPageSize());
        domainQuery.setRoleName(query.getRoleName());
        domainQuery.setRoleKey(query.getRoleKey());
        domainQuery.setStatus(query.getStatus());
        domainQuery.setBeginTime(query.getBeginTime());
        domainQuery.setEndTime(query.getEndTime());
        return roleAssembler.toPage(roleRepository.search(domainQuery));
    }

    /**
     * 查询角色详情。
     *
     * @param id 角色 ID
     * @return 角色详情
     */
    @Override
    public RoleResp getRoleDetail(String id) {
        return roleAssembler.toResp(requireRole(IdParser.parseId(id)));
    }

    /**
     * 查询角色下拉选项。
     *
     * @return 角色选项列表
     */
    @Override
    public List<RoleOptionResp> listOptions() {
        return userAssembler.toRoleOptions(roleRepository.findAll());
    }

    /**
     * 新增角色。
     *
     * @param req 角色参数
     * @return 新角色 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRole(RoleSaveReq req) {
        if (roleRepository.existsByRoleKey(req.getRoleKey(), null)) {
            throw new BusinessException(ResultCode.DATA_EXIST, "角色权限字符串已存在");
        }
        Role role = new Role();
        fillRole(role, req);
        role.assignMenus(IdParser.parseIds(req.getMenuIds()));
        roleRepository.save(role);
        // 角色与菜单授权影响的是"一批用户"，无法逐个定位，只能整体失效
        loginUserCache.clear();
        return String.valueOf(role.getId());
    }

    /**
     * 修改角色（menuIds 为空集合表示清空权限；为 null 表示不改权限）。
     *
     * @param req 角色参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleSaveReq req) {
        Long roleId = IdParser.parseId(req.getId());
        Role role = requireRole(roleId);
        if (roleRepository.existsByRoleKey(req.getRoleKey(), roleId)) {
            throw new BusinessException(ResultCode.DATA_EXIST, "角色权限字符串已存在");
        }
        fillRole(role, req);
        if (req.getMenuIds() != null) {
            role.assignMenus(IdParser.parseIds(req.getMenuIds()));
        }
        roleRepository.save(role);
        // 角色与菜单授权影响的是"一批用户"，无法逐个定位，只能整体失效
        loginUserCache.clear();
    }

    /**
     * 批量删除角色（内置角色、已分配用户的角色不可删除）。
     *
     * @param ids 角色 ID 集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRoles(List<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        List<Long> roleIds = new ArrayList<>();
        for (String id : ids) {
            roleIds.add(IdParser.parseId(id));
        }
        // 批量取角色 + 批量统计用户关联：循环里逐条查会随删除数量线性放大查询次数
        Map<Long, Role> roles = roleRepository.findByIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, role -> role, (a, b) -> a));
        Map<Long, Long> userCounts = roleRepository.countUsersByRoleIds(roleIds);
        for (Long roleId : roleIds) {
            Role role = roles.get(roleId);
            if (role == null) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "角色不存在");
            }
            // 内置角色保护由聚合规则判定
            if (role.isBuiltin()) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "内置超级管理员角色不可删除");
            }
            // 已分配给用户的角色不可删除，避免产生"无角色用户"（跨聚合校验属应用层职责）
            if (userCounts.getOrDefault(roleId, 0L) > 0) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "角色已分配给用户，请先解除关联");
            }
        }
        roleRepository.deleteByIds(roleIds);
    }

    /**
     * 修改角色状态。
     *
     * @param req 状态参数
     */
    @Override
    public void updateStatus(UpdateStatusReq req) {
        Role role = requireRole(IdParser.parseId(req.getId()));
        try {
            role.changeStatus(req.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BIZ_ERROR, "内置超级管理员角色不可停用");
        }
        roleRepository.save(role);
        // 角色与菜单授权影响的是"一批用户"，无法逐个定位，只能整体失效
        loginUserCache.clear();
    }

    /**
     * 给角色分配菜单权限。
     *
     * @param req 分配参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(AssignMenuReq req) {
        Role role = requireRole(IdParser.parseId(req.getRoleId()));
        List<Long> menuIds = IdParser.parseIds(req.getMenuIds());
        assertMenusAssignable(menuIds);
        role.assignMenus(menuIds);
        roleRepository.save(role);
        // 角色与菜单授权影响的是"一批用户"，无法逐个定位，只能整体失效
        loginUserCache.clear();
    }

    /**
     * 校验"操作者是否有权授予这些菜单"（防垂直越权）。
     *
     * <p>与改角色是同一类问题：持有 {@code system:role:assign} 的管理员若能把任意菜单挂到
     * 自己所在的角色上，就等于给自己发权限 —— 下一步请求重新加载权限后即完成提权。
     * 因此待授予的菜单权限必须是操作者自己已经拥有的。
     *
     * @param menuIds 待授予的菜单 ID
     * @throws BusinessException 越权时抛出
     */
    private void assertMenusAssignable(List<Long> menuIds) {
        LoginUser operator = operatorContext.current();
        if (operator == null || operator.isSuperAdmin() || CollUtil.isEmpty(menuIds)) {
            return;
        }
        Set<String> owned = operator.getPermissions() == null ? Set.of() : operator.getPermissions();
        for (Menu menu : menuRepository.findByIds(menuIds)) {
            String permission = menu.getPermission();
            if (permission == null || permission.isBlank()) {
                continue;
            }
            if (!owned.contains(permission.trim())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权授予超出自身权限范围的菜单");
            }
        }
    }

    /**
     * 按 ID 加载角色，不存在抛出业务异常。
     *
     * @param id 角色 ID
     * @return 角色聚合
     */
    private Role requireRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND));
    }

    /**
     * 填充角色属性。
     *
     * @param role 角色聚合
     * @param req  请求参数
     */
    private void fillRole(Role role, RoleSaveReq req) {
        role.setRoleName(req.getRoleName());
        role.setRoleKey(req.getRoleKey());
        role.setSort(req.getSort() == null ? 0 : req.getSort());
        role.setStatus(req.getStatus() == null ? Constants.STATUS_NORMAL : req.getStatus());
        role.setRemark(req.getRemark());
    }

}
