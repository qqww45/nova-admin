package com.example.admin.interfaces.controller;

import com.example.admin.application.service.ImportExportAppService;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.ReasonReq;
import com.example.admin.interfaces.dto.resp.ImportBatchResp;
import com.example.admin.interfaces.dto.resp.ImportCommitResp;
import com.example.admin.interfaces.dto.resp.ImportPreviewResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 导入导出控制器（附录A：三步法导入、双模板导出、错误清单、一键回滚）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/biz/import")
@Tag(name = "导入导出", description = "xlsx 导入三步法与双模板导出")
public class BizImportExportController {

    private final ImportExportAppService importExportService;

    /**
     * 第一步：上传解析（只报告不落库）。
     *
     * @param type       模块：CUSTOMER / ORDER / SHIPMENT
     * @param file       上传文件
     * @param sheetName  工作表名
     * @param idempotent 幂等策略
     * @return 预览报告
     */
    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermi('biz:shipment:import')")
    @Operation(summary = "导入预览（只校验不导入）")
    public R<ImportPreviewResp> preview(@RequestParam String type,
                                        @RequestParam MultipartFile file,
                                        @RequestParam(required = false) String sheetName,
                                        @RequestParam(defaultValue = "SKIP") String idempotent) throws IOException {
        return R.ok(importExportService.preview(type, file.getBytes(), file.getOriginalFilename(),
                sheetName, idempotent));
    }

    /**
     * 第三步：确认导入。
     *
     * @param type       模块
     * @param file       上传文件
     * @param sheetName  工作表名
     * @param idempotent 幂等策略
     * @return 导入结果
     */
    @PostMapping("/confirm")
    @PreAuthorize("@ss.hasPermi('biz:shipment:import')")
    @Operation(summary = "确认导入")
    public R<ImportCommitResp> confirm(@RequestParam String type,
                                       @RequestParam MultipartFile file,
                                       @RequestParam(required = false) String sheetName,
                                       @RequestParam(defaultValue = "SKIP") String idempotent) throws IOException {
        return R.ok(importExportService.confirm(type, file.getBytes(), file.getOriginalFilename(),
                sheetName, idempotent));
    }

    /**
     * 下载导入模板。
     *
     * @param type     模块
     * @param template 模板：SYSTEM / LEGACY
     * @param response 响应
     */
    @GetMapping("/template")
    @Operation(summary = "下载导入模板")
    public void template(@RequestParam String type,
                         @RequestParam(defaultValue = "SYSTEM") String template,
                         HttpServletResponse response) throws IOException {
        byte[] bytes = importExportService.template(type, template);
        download(response, bytes, fileName(type + "-导入模板-" + ("LEGACY".equalsIgnoreCase(template) ? "传统台账" : "系统模板")));
    }

    /**
     * 导出数据。
     *
     * @param type     模块
     * @param template 模板：SYSTEM / LEGACY
     * @param keyword  关键字
     * @param beginDate 开始日期
     * @param endDate  结束日期
     * @param response 响应
     */
    @GetMapping("/export")
    @Operation(summary = "导出数据")
    public void export(@RequestParam String type,
                       @RequestParam(defaultValue = "SYSTEM") String template,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String beginDate,
                       @RequestParam(required = false) String endDate,
                       HttpServletResponse response) throws IOException {
        byte[] bytes = importExportService.export(type, template, keyword, beginDate, endDate, null);
        download(response, bytes, fileName(type + "-导出-" + LocalDate.now()));
    }

    /**
     * 历史导入批次。
     *
     * @param type  模块
     * @param limit 条数上限
     * @return 批次列表
     */
    @GetMapping("/batches")
    @Operation(summary = "历史导入批次")
    public R<List<ImportBatchResp>> batches(@RequestParam(required = false) String type,
                                            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(importExportService.batches(type, limit));
    }

    /**
     * 回滚导入批次（敏感操作，需填原因）。
     *
     * @param batchId 批次 ID
     * @param req     回滚原因
     * @return 操作结果
     */
    @PutMapping("/{batchId}/rollback")
    @PreAuthorize("@ss.hasPermi('biz:import:rollback')")
    @Operation(summary = "回滚导入批次")
    public R<Void> rollback(@PathVariable String batchId, @Valid @RequestBody(required = false) ReasonReq req) {
        importExportService.rollback(batchId, req == null ? "" : req.getReason());
        return R.ok();
    }

    /**
     * 下载某批次的《错误清单 xlsx》。
     *
     * @param batchId  批次 ID
     * @param response 响应
     */
    @GetMapping("/{batchId}/errors")
    @PreAuthorize("@ss.hasPermi('biz:import:error')")
    @Operation(summary = "下载错误清单")
    public void errors(@PathVariable String batchId, HttpServletResponse response) throws IOException {
        byte[] bytes = importExportService.errorReport(batchId);
        if (bytes == null) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":1001,\"msg\":\"该批次没有失败行，无需下载错误清单\",\"data\":null}");
            return;
        }
        download(response, bytes, "导入错误清单-" + batchId);
    }

    /* ---------------- 私有辅助 ---------------- */

    private void download(HttpServletResponse response, byte[] bytes, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private String fileName(String prefix) {
        return prefix;
    }
}
