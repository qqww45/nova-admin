package com.example.admin.interfaces.controller;

import cn.hutool.core.util.StrUtil;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.AssignMenuReq;
import com.example.admin.interfaces.dto.req.RolePageQuery;
import com.example.admin.interfaces.dto.req.RoleSaveReq;
import com.example.admin.interfaces.dto.req.UpdateStatusReq;
import com.example.admin.interfaces.dto.resp.RoleOptionResp;
import com.example.admin.interfaces.dto.resp.RoleResp;
import com.example.admin.application.service.RoleAppService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 角色管理控制器：角色 CRUD 与菜单权限分配。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/role")
@Tag(name = "角色管理", description = "角色增删改查与菜单权限分配")
public class SysRoleController {

    private final RoleAppService roleService;

    /**
     * 分页查询角色。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @Operation(summary = "分页查询角色")
    public R<PageResult<RoleResp>> list(@Valid RolePageQuery query) {
        return R.ok(roleService.pageRoles(query));
    }

    /**
     * 查询角色详情（含已分配菜单）。
     *
     * @param id 角色 ID
     * @return 角色详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @Operation(summary = "查询角色详情")
    public R<RoleResp> detail(@PathVariable String id) {
        return R.ok(roleService.getRoleDetail(id));
    }

    /**
     * 查询角色下拉选项。
     *
     * @return 角色选项列表
     */
    @GetMapping("/options")
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @Operation(summary = "查询角色下拉选项")
    public R<List<RoleOptionResp>> options() {
        return R.ok(roleService.listOptions());
    }

    /**
     * 新增角色。
     *
     * @param req 角色参数
     * @return 新角色 ID
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @Operation(summary = "新增角色")
    public R<String> add(@Valid @RequestBody RoleSaveReq req) {
        return R.ok(roleService.createRole(req));
    }

    /**
     * 修改角色。
     *
     * @param req 角色参数
     * @return 空响应
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Operation(summary = "修改角色")
    public R<Void> edit(@Valid @RequestBody RoleSaveReq req) {
        roleService.updateRole(req);
        return R.ok();
    }

    /**
     * 批量删除角色。
     *
     * @param ids 角色 ID，多个用逗号分隔
     * @return 空响应
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    @Operation(summary = "批量删除角色", description = "内置角色与已分配用户的角色不可删除")
    public R<Void> remove(@PathVariable String ids) {
        roleService.removeRoles(splitIds(ids));
        return R.ok();
    }

    /**
     * 修改角色状态。
     *
     * @param req 状态参数
     * @return 空响应
     */
    @PutMapping("/status")
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Operation(summary = "修改角色状态")
    public R<Void> updateStatus(@Valid @RequestBody UpdateStatusReq req) {
        roleService.updateStatus(req);
        return R.ok();
    }

    /**
     * 给角色分配菜单权限。
     *
     * @param req 分配参数
     * @return 空响应
     */
    @PutMapping("/assignMenu")
    @PreAuthorize("@ss.hasPermi('system:role:assign')")
    @Operation(summary = "分配菜单权限")
    public R<Void> assignMenu(@Valid @RequestBody AssignMenuReq req) {
        roleService.assignMenus(req);
        return R.ok();
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
