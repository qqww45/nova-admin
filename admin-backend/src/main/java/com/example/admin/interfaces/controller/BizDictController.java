package com.example.admin.interfaces.controller;

import com.example.admin.application.service.DictAppService;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.resp.DictResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务字典控制器（税率等禁止前端硬编码）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/dict")
@Tag(name = "业务字典", description = "税率、币种、结算方式、交货方式")
public class BizDictController {

    private final DictAppService dictService;

    /**
     * 查询全部字典（前端一次性缓存）。
     *
     * @return 字典项
     */
    @GetMapping("/all")
    @Operation(summary = "查询全部字典")
    public R<List<DictResp>> all() {
        return R.ok(dictService.listAll());
    }

    /**
     * 按编码查询字典。
     *
     * @param dictCode 字典编码
     * @return 字典项
     */
    @GetMapping("/{dictCode}")
    @Operation(summary = "按编码查询字典")
    public R<List<DictResp>> byCode(@PathVariable String dictCode) {
        return R.ok(dictService.listByCode(dictCode));
    }
}
