package com.example.admin.interfaces.controller;

import com.example.admin.application.service.KdConfigAppService;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.KdFieldMappingSaveReq;
import com.example.admin.interfaces.dto.req.KdTaskSaveReq;
import com.example.admin.interfaces.dto.resp.KdFieldMappingResp;
import com.example.admin.interfaces.dto.resp.KdSyncTaskResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 同步配置控制器（任务清单 + 字段映射）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/kd/config")
@Tag(name = "金蝶同步配置", description = "任务清单与账套字段映射")
public class KdConfigController {

    private final KdConfigAppService configService;

    /**
     * 查询任务清单。
     *
     * @return 任务列表
     */
    @GetMapping("/task/list")
    @PreAuthorize("@ss.hasPermi('kd:task:list')")
    @Operation(summary = "查询同步任务")
    public R<List<KdSyncTaskResp>> tasks() {
        return R.ok(configService.listTasks());
    }

    /**
     * 保存任务。
     *
     * @param req 请求
     * @return 任务 ID
     */
    @PostMapping("/task")
    @PreAuthorize("@ss.hasPermi('kd:task:edit')")
    @Operation(summary = "保存同步任务")
    public R<String> saveTask(@Valid @RequestBody KdTaskSaveReq req) {
        return R.ok(configService.saveTask(req));
    }

    /**
     * 查询字段映射。
     *
     * @param bindingId 绑定 ID
     * @return 映射列表
     */
    @GetMapping("/mapping")
    @PreAuthorize("@ss.hasPermi('kd:mapping:list')")
    @Operation(summary = "查询账套字段映射")
    public R<List<KdFieldMappingResp>> mappings(@RequestParam String bindingId) {
        return R.ok(configService.listMappings(bindingId));
    }

    /**
     * 保存字段映射。
     *
     * @param req 请求
     * @return 操作结果
     */
    @PostMapping("/mapping")
    @PreAuthorize("@ss.hasPermi('kd:mapping:edit')")
    @Operation(summary = "保存账套字段映射")
    public R<Void> saveMapping(@Valid @RequestBody KdFieldMappingSaveReq req) {
        configService.saveMapping(req);
        return R.ok();
    }
}
