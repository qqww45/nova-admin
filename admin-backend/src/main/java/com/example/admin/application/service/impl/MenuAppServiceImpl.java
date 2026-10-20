package com.example.admin.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.application.assembler.MenuAssembler;
import com.example.admin.application.service.MenuAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.menu.Menu;
import com.example.admin.domain.model.role.Role;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.port.LoginUserCache;
import com.example.admin.domain.repository.MenuRepository;
import com.example.admin.domain.repository.RoleRepository;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.service.MenuTreeDomainService;
import com.example.admin.interfaces.dto.req.MenuSaveReq;
import com.example.admin.interfaces.dto.resp.MenuVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单应用服务实现。
 *
 * <p>编排要点：
 * <ul>
 *     <li>树形组装调用领域服务 {@link MenuTreeDomainService}（应用层不写递归算法）；</li>
 *     <li>用户可见菜单的判定使用菜单实体自身的 {@code isRoutable()} 规则；</li>
 *     <li>删除前用领域服务收集子树 ID，保证不产生孤儿节点。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuAppServiceImpl implements MenuAppService {

    private final MenuRepository menuRepository;

    private final LoginUserCache loginUserCache;

    private final RoleRepository roleRepository;

    private final UserRepository userRepository;

    private final MenuTreeDomainService menuTreeDomainService;

    private final MenuAssembler menuAssembler;

    /**
     * 查询菜单树（管理用）。
     *
     * @param menuName 菜单名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 菜单树
     */
    @Override
    public List<MenuVo> tree(String menuName, Integer status) {
        List<Menu> menus = menuRepository.findAll(menuName, status);
        return menuAssembler.toVoList(menuTreeDomainService.buildTree(menus));
    }

    /**
     * 构建当前用户的菜单树（前端动态路由数据源）。
     *
     * @param userId 用户 ID
     * @return 菜单树
     */
    @Override
    public List<MenuVo> buildUserMenuTree(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<Menu> menus;
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && isSuperAdmin(user)) {
            // 超级管理员：全部可路由菜单
            menus = menuRepository.findAll(null, Constants.STATUS_NORMAL);
        } else if (user == null) {
            menus = new ArrayList<>();
        } else {
            List<Long> effectiveRoleIds = effectiveRoleIdsOf(user);
            if (CollUtil.isEmpty(effectiveRoleIds)) {
                return new ArrayList<>();
            }
            menus = menuRepository.findByRoleIds(effectiveRoleIds);
        }
        // 领域规则过滤：只有目录/菜单且启用可见的节点进入前端路由
        List<Menu> routable = menus.stream().filter(Menu::isRoutable).collect(Collectors.toList());
        return menuAssembler.toVoList(menuTreeDomainService.buildTree(routable));
    }

    /**
     * 查询角色已分配的菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 集合
     */
    @Override
    public List<String> selectMenuIdsByRoleId(Long roleId) {
        return menuRepository.findMenuIdsByRoleId(roleId).stream().map(String::valueOf).collect(Collectors.toList());
    }

    /**
     * 新增菜单。
     *
     * @param req 菜单参数
     * @return 新菜单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(MenuSaveReq req) {
        checkParent(req.getParentId(), null);
        Menu menu = new Menu();
        fillMenu(menu, req);
        menuRepository.save(menu);
        // 菜单的权限标识变了，会改变一批用户能访问什么：整体失效
        loginUserCache.clear();
        return String.valueOf(menu.getId());
    }

    /**
     * 修改菜单（父菜单不能是自己或自己的子孙）。
     *
     * @param req 菜单参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(MenuSaveReq req) {
        Long menuId = IdParser.parseId(req.getId());
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND));
        checkParent(req.getParentId(), menuId);
        fillMenu(menu, req);
        menuRepository.save(menu);
        // 菜单的权限标识变了，会改变一批用户能访问什么：整体失效
        loginUserCache.clear();
    }

    /**
     * 批量删除菜单（级联子菜单）。
     *
     * @param ids 菜单 ID 集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        List<Long> menuIds = new ArrayList<>();
        for (String id : ids) {
            menuIds.add(IdParser.parseId(id));
        }
        // 由领域服务递归收集子树，保证删除后不产生孤儿节点
        List<Menu> all = menuRepository.findAll(null, null);
        List<Long> allIds = menuTreeDomainService.collectSubTreeIds(all, menuIds);
        menuRepository.deleteByIds(allIds);
        loginUserCache.clear();
    }

    /**
     * 校验父菜单合法性（存在性 + 环路检测）。
     *
     * @param parentId 父菜单 ID（字符串）
     * @param selfId   当前菜单 ID（新增时为 null）
     */
    private void checkParent(String parentId, Long selfId) {
        Long pid = StrUtil.isBlank(parentId) ? Constants.TOP_PARENT_ID : IdParser.parseId(parentId);
        if (selfId != null && selfId.equals(pid)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上级菜单不能是自己");
        }
        if (Constants.TOP_PARENT_ID.equals(pid)) {
            return;
        }
        if (menuRepository.findById(pid).isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上级菜单不存在");
        }
        if (selfId != null) {
            List<Menu> all = menuRepository.findAll(null, null);
            List<Long> subTreeIds = menuTreeDomainService.collectSubTreeIds(all, List.of(selfId));
            if (subTreeIds.contains(pid)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "上级菜单不能是自己的子菜单");
            }
        }
    }

    /**
     * 判断用户是否为超级管理员。
     *
     * @param user 用户聚合
     * @return 是否超级管理员
     */
    private boolean isSuperAdmin(User user) {
        if (user.isSuperAdmin()) {
            return true;
        }
        List<Role> roles = roleRepository.findByIds(user.getRoleIds());
        return roles.stream().anyMatch(role -> role.isEnabled() && role.isBuiltin());
    }

    /**
     * 计算用户启用状态的角色 ID。
     *
     * @param user 用户聚合
     * @return 生效角色 ID 集合
     */
    private List<Long> effectiveRoleIdsOf(User user) {
        return roleRepository.findByIds(user.getRoleIds()).stream()
                .filter(Role::isEnabled)
                .map(Role::getId)
                .collect(Collectors.toList());
    }

    /**
     * 填充菜单属性。
     *
     * @param menu 菜单实体
     * @param req  请求参数
     */
    private void fillMenu(Menu menu, MenuSaveReq req) {
        menu.setParentId(StrUtil.isBlank(req.getParentId()) ? Constants.TOP_PARENT_ID : IdParser.parseId(req.getParentId()));
        menu.setMenuName(req.getMenuName());
        menu.setMenuType(req.getMenuType() == null ? Constants.MENU_TYPE_MENU : req.getMenuType());
        menu.setPath(req.getPath());
        menu.setComponent(req.getComponent());
        menu.setPermission(req.getPermission());
        menu.setIcon(req.getIcon());
        menu.setSort(req.getSort() == null ? 0 : req.getSort());
        menu.setVisible(req.getVisible() == null ? 1 : req.getVisible());
        menu.setStatus(req.getStatus() == null ? Constants.STATUS_NORMAL : req.getStatus());
        menu.setRemark(req.getRemark());
    }

}
