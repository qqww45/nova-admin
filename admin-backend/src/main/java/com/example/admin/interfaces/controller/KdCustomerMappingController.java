package com.example.admin.interfaces.controller;

import com.example.admin.application.service.KdCustomerMappingAppService;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.KdMappingSaveReq;
import com.example.admin.interfaces.dto.resp.KdCustomerMappingResp;
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
 * 金蝶客户 ↔ 内部客户映射控制器（试点批 R4）。
 *
 * <p>两套客户体系的桥；数据隔离由服务层强制（业务员只维护本人账套的映射）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Tag(name = "金蝶客户映射", description = "金蝶客户与内部客户的对应关系维护")
@RestController
@RequestMapping("/kd/mapping")
@Validated
@RequiredArgsConstructor
public class KdCustomerMappingController {

    private final KdCustomerMappingAppService mappingService;

    /**
     * 映射行列表。
     *
     * @param acctId 账套过滤
     * @param status 状态过滤（UNMATCHED / SUGGESTED / CONFIRMED）
     * @param keyword 金蝶客户名称/编码关键字
     * @param ownerUserId 归属人过滤（仅管理员生效）
     * @return 映射行列表
     */
    @Operation(summary = "映射行列表", description = "一个金蝶客户一行，含未匹配 / 建议 / 已确认状态")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('kd:customer-mapping:list')")
    public R<List<KdCustomerMappingResp>> list(
            @RequestParam(required = false) String acctId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ownerUserId) {
        return R.ok(mappingService.list(acctId, status, keyword, ownerUserId));
    }

    /**
     * 自动匹配建议。
     *
     * @param acctId 账套；空 = 全部账套
     * @param ownerUserId 归属人（仅管理员生效）
     * @return 建议数与跳过数
     */
    @Operation(summary = "自动匹配建议",
            description = "名称标准化匹配唯一命中的内部客户，生成待确认建议；同名歧义不生成")
    @PostMapping("/auto-suggest")
    @PreAuthorize("@ss.hasPermi('kd:customer-mapping:edit')")
    public R<KdCustomerMappingAppService.AutoSuggestResult> autoSuggest(
            @RequestParam(required = false) String acctId,
            @RequestParam(required = false) String ownerUserId) {
        return R.ok(mappingService.autoSuggest(acctId, ownerUserId));
    }

    /**
     * 人工保存映射（视为已确认）。
     *
     * @param req 请求
     * @return 操作结果
     */
    @Operation(summary = "保存映射", description = "业务员人工指定对应关系，已有建议行升级为已确认")
    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermi('kd:customer-mapping:edit')")
    public R<Void> save(@Valid @RequestBody KdMappingSaveReq req) {
        mappingService.save(req);
        return R.ok();
    }
}
