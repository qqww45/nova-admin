package com.example.admin.interfaces.controller;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.service.DeptAppService;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.DeptSaveReq;
import com.example.admin.interfaces.dto.resp.DeptVo;
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
 * 部门管理控制器（Interfaces 层）。
 *
 * <p>本层只做三件事：接收并校验参数、调用应用服务、封装统一响应。
 * 业务逻辑与持久化细节一律不可出现在这里。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/dept")
@Tag(name = "部门管理", description = "部门树增删改查")
public class SysDeptController {

    private final DeptAppService deptAppService;

    /**
     * 查询部门树。
     *
     * @param deptName 部门名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 部门树
     */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @Operation(summary = "查询部门树")
    public R<List<DeptVo>> tree(@RequestParam(required = false) String deptName,
                                @RequestParam(required = false) Integer status) {
        return R.ok(deptAppService.tree(deptName, status));
    }

    /**
     * 查询部门详情。
     *
     * @param id 部门 ID
     * @return 部门树（单根，便于前端复用树选择器）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('system:dept:query')")
    @Operation(summary = "查询部门详情")
    public R<List<DeptVo>> detail(@PathVariable String id) {
        return R.ok(deptAppService.tree(null, null));
    }

    /**
     * 新增部门。
     *
     * @param req 部门参数
     * @return 新部门 ID
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:dept:add')")
    @Operation(summary = "新增部门")
    public R<String> add(@Valid @RequestBody DeptSaveReq req) {
        return R.ok(deptAppService.create(req));
    }

    /**
     * 修改部门。
     *
     * @param req 部门参数
     * @return 空响应
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:dept:edit')")
    @Operation(summary = "修改部门")
    public R<Void> edit(@Valid @RequestBody DeptSaveReq req) {
        deptAppService.update(req);
        return R.ok();
    }

    /**
     * 批量删除部门（含子部门）。
     *
     * @param ids 部门 ID，多个用逗号分隔
     * @return 空响应
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('system:dept:remove')")
    @Operation(summary = "批量删除部门", description = "部门下存在用户时不允许删除")
    public R<Void> remove(@PathVariable String ids) {
        deptAppService.remove(splitIds(ids));
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
