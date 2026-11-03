package com.example.admin.interfaces.controller;

import com.example.admin.application.service.StatementAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.ReasonReq;
import com.example.admin.interfaces.dto.req.StatementGenerateReq;
import com.example.admin.interfaces.dto.req.StatementPageQuery;
import com.example.admin.interfaces.dto.req.StatementPreviewReq;
import com.example.admin.interfaces.dto.resp.ReceivableSummaryResp;
import com.example.admin.interfaces.dto.resp.StatementPreviewResp;
import com.example.admin.interfaces.dto.resp.StatementResp;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 对账单控制器（客户应收账款对账单：预览 / 生成 / 签发 / 确认 / 导出）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/statement")
@Tag(name = "对账单", description = "客户应收账款对账单")
public class BizStatementController {

    private final StatementAppService statementService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('biz:statement:list')")
    @Operation(summary = "分页查询对账单")
    public R<PageResult<StatementResp>> list(@Valid StatementPageQuery query) {
        return R.ok(statementService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('biz:statement:list')")
    @Operation(summary = "查询对账单详情")
    public R<StatementResp> detail(@PathVariable String id) {
        return R.ok(statementService.detail(id));
    }

    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermi('biz:statement:list')")
    @Operation(summary = "实时预览对账单（不落库）")
    public R<StatementPreviewResp> preview(@Valid @RequestBody StatementPreviewReq req) {
        return R.ok(statementService.preview(req));
    }

    /**
     * 客户应收速览（催款优先级）。
     *
     * <p>全期间期末应收余额按倒序，最近出货/收款时间标注"欠了多久"。
     * 权限复用对账单查询，不新增菜单。
     *
     * @return 应收速览列表
     */
    @GetMapping("/receivable-summary")
    @PreAuthorize("@ss.hasPermi('biz:statement:list')")
    @Operation(summary = "客户应收速览", description = "期初 + 累计出货 − 退货 − 累计收款，按余额倒序")
    public R<List<ReceivableSummaryResp>> receivableSummary() {
        return R.ok(statementService.receivableSummaries());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('biz:statement:add')")
    @Operation(summary = "生成并保存对账单快照")
    public R<String> generate(@Valid @RequestBody StatementGenerateReq req) {
        return R.ok(statementService.generate(req));
    }

    @PutMapping("/{id}/issue")
    @PreAuthorize("@ss.hasPermi('biz:statement:issue')")
    @Operation(summary = "签发对账单")
    public R<Void> issue(@PathVariable String id) {
        statementService.issue(id);
        return R.ok();
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPermi('biz:statement:confirm')")
    @Operation(summary = "客户确认（回签无误）")
    public R<Void> confirm(@PathVariable String id, @Valid @RequestBody ReasonReq req) {
        statementService.confirm(id, req.getReason());
        return R.ok();
    }

    @PutMapping("/{id}/different")
    @PreAuthorize("@ss.hasPermi('biz:statement:confirm')")
    @Operation(summary = "标记差异")
    public R<Void> markDifferent(@PathVariable String id, @Valid @RequestBody ReasonReq req) {
        statementService.markDifferent(id, req.getReason());
        return R.ok();
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermi('biz:statement:export')")
    @Operation(summary = "导出对账单 Excel（传统台账格式）")
    public void export(@RequestParam String id, HttpServletResponse response) throws IOException {
        byte[] bytes = statementService.export(id);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode("对账单-" + id, StandardCharsets.UTF_8) + ".xlsx");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
