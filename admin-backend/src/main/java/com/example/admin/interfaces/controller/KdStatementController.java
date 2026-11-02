package com.example.admin.interfaces.controller;

import com.example.admin.application.service.KdStatementAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.KdStatementQuery;
import com.example.admin.interfaces.dto.resp.KdStatementDetailResp;
import com.example.admin.interfaces.dto.resp.KdStatementSummaryResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 金蝶对账单接口（只读，数据来自采集器上报的 kd_outbound / kd_outbound_line）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Tag(name = "金蝶对账单", description = "按客户/期间汇总金蝶出库数据并导出")
@RestController
@RequestMapping("/kd/statement")
@Validated
@RequiredArgsConstructor
public class KdStatementController {

    private final KdStatementAppService statementAppService;

    /**
     * 按客户汇总。
     *
     * @param query 查询条件
     * @return 分页汇总
     */
    @Operation(summary = "对账单汇总", description = "按客户 + 期间汇总出库单数与金额")
    @GetMapping("/summary")
    @PreAuthorize("@ss.hasPermi('kd:statement:list')")
    public R<PageResult<KdStatementSummaryResp>> summary(@Valid KdStatementQuery query) {
        return R.ok(statementAppService.summary(query));
    }

    /**
     * 单客户明细。
     *
     * @param query 查询条件（customerFid 必填）
     * @return 明细
     */
    @Operation(summary = "对账单明细", description = "指定客户在期间内的出库单与分录")
    @GetMapping("/detail")
    @PreAuthorize("@ss.hasPermi('kd:statement:list')")
    public R<KdStatementDetailResp> detail(@Valid KdStatementQuery query) {
        return R.ok(statementAppService.detail(query));
    }

    /**
     * 导出 xlsx。
     *
     * @param query 查询条件
     * @param response 响应（直接写流，与既有导出保持一致）
     * @throws IOException 写流失败
     */
    @Operation(summary = "导出对账单", description = "导出客户汇总 + 出库明细两个工作表")
    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermi('kd:statement:export')")
    public void export(@Valid KdStatementQuery query, HttpServletResponse response) throws IOException {
        byte[] bytes = statementAppService.export(query);
        String fileName = "金蝶对账单-" + (query.getDateFrom() == null ? "" : query.getDateFrom())
                + "_" + (query.getDateTo() == null ? "" : query.getDateTo());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename="
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
