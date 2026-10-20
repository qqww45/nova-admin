package com.example.admin.interfaces.controller;

import com.example.admin.application.service.LogAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.resp.OperationLogResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务操作日志控制器（M6 审计）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/log")
@Tag(name = "业务操作日志", description = "出货 / 导入 / 回滚 / 作废 / 对账 的审计流水")
public class BizLogController {

    private final LogAppService logService;

    /**
     * 分页查询操作日志。
     *
     * @param module   模块（为空表示全部）
     * @param action   动作（为空表示全部）
     * @param keyword  关键字：业务 ID / 原因 / 操作人
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('biz:log:list')")
    @Operation(summary = "分页查询操作日志")
    public R<PageResult<OperationLogResp>> list(@RequestParam(required = false) String module,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(logService.page(module, action, keyword, pageNum, pageSize));
    }

    /**
     * 查询某个业务单据的变更记录。
     *
     * @param module 模块
     * @param bizId  业务 ID
     * @param limit  条数上限
     * @return 变更记录
     */
    @GetMapping("/biz/{module}/{bizId}")
    @PreAuthorize("@ss.hasPermi('biz:log:list')")
    @Operation(summary = "查询单据变更记录")
    public R<List<OperationLogResp>> listByBiz(@PathVariable String module,
                                               @PathVariable String bizId,
                                               @RequestParam(defaultValue = "20") int limit) {
        return R.ok(logService.listByBiz(module, bizId, limit));
    }
}
