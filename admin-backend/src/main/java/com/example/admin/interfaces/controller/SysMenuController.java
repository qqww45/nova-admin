package com.example.admin.interfaces.controller;

import cn.hutool.core.util.StrUtil;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.MenuSaveReq;
import com.example.admin.interfaces.dto.resp.MenuVo;
import com.example.admin.application.service.MenuAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 菜单管理控制器。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/menu")
@Tag(name = "菜单管理", description = "菜单（目录/菜单/按钮）增删改查与树形展示")
public class SysMenuController {

    private final MenuAppService menuService;

    /**
     * 查询菜单树（含按钮，管理用）。
     *
     * @param menuName 菜单名称（模糊匹配，可为空）
     * @param status   状态（可为空）
     * @return 菜单树
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    @Operation(summary = "查询菜单树")
    public R<List<MenuVo>> list(@RequestParam(required = false) String menuName,
                                @RequestParam(required = false) Integer status) {
        return R.ok(menuService.tree(menuName, status));
    }

    /**
     * 查询菜单下拉树（仅目录与菜单，用于选择上级菜单）。
     *
     * @return 菜单树
     */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    @Operation(summary = "查询菜单下拉树")
    public R<List<MenuVo>> tree() {
        List<MenuVo> tree = menuService.tree(null, 0);
        return R.ok(tree);
    }

    /**
     * 查询菜单详情。
     *
     * @param id 菜单 ID
     * @return 菜单详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('system:menu:query')")
    @Operation(summary = "查询菜单详情")
    public R<List<MenuVo>> detail(@PathVariable String id) {
        // 返回单元素集合，便于前端直接复用树形选择器逻辑
        return R.ok(List.of(findById(id)));
    }

    /**
     * 新增菜单。
     *
     * @param req 菜单参数
     * @return 新菜单 ID
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:menu:add')")
    @Operation(summary = "新增菜单")
    public R<String> add(@Valid @RequestBody MenuSaveReq req) {
        return R.ok(menuService.create(req));
    }

    /**
     * 修改菜单。
     *
     * @param req 菜单参数
     * @return 空响应
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:menu:edit')")
    @Operation(summary = "修改菜单")
    public R<Void> edit(@Valid @RequestBody MenuSaveReq req) {
        menuService.update(req);
        return R.ok();
    }

    /**
     * 批量删除菜单（含子菜单）。
     *
     * @param ids 菜单 ID，多个用逗号分隔
     * @return 空响应
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('system:menu:remove')")
    @Operation(summary = "批量删除菜单", description = "会级联删除子菜单及其角色关联")
    public R<Void> remove(@PathVariable String ids) {
        menuService.remove(splitIds(ids));
        return R.ok();
    }

    /**
     * 查询单个菜单（平铺查找，因为 tree 返回的是树形结构）。
     *
     * @param id 菜单 ID
     * @return 菜单 VO
     */
    private MenuVo findById(String id) {
        List<MenuVo> all = menuService.tree(null, null);
        MenuVo found = search(all, id);
        if (found == null) {
            throw new com.example.admin.common.exception.BusinessException(
                    com.example.admin.common.result.ResultCode.DATA_NOT_FOUND);
        }
        return found;
    }

    /**
     * 递归查找指定 ID 的菜单节点。
     *
     * @param menus 菜单树
     * @param id    菜单 ID
     * @return 命中的菜单 VO，未找到返回 null
     */
    private MenuVo search(List<MenuVo> menus, String id) {
        for (MenuVo menu : menus) {
            if (id.equals(menu.getId())) {
                return menu;
            }
            MenuVo found = search(menu.getChildren(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 将逗号分隔的 ID 字符串拆分为列表。
     *
     * @param ids ID 字符串
     * @return ID 列表
     */
    private List<String> splitIds(String ids) {
        if (StrUtil.isBlank(ids)) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
