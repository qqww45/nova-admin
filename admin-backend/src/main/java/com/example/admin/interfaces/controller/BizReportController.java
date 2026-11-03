package com.example.admin.interfaces.controller;

import com.example.admin.application.service.ReportAppService;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.resp.CustomerSummaryResp;
import com.example.admin.interfaces.dto.resp.HealthReportResp;
import com.example.admin.interfaces.dto.resp.OpenOrderItemResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表与体检控制器（M5 / 附录A A.6）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/report")
@Tag(name = "业务报表", description = "历史数据体检与客户出货汇总")
public class BizReportController {

    private final ReportAppService reportService;

    /**
     * 历史数据体检报告。
     *
     * @return 体检报告
     */
    @GetMapping("/health")
    @PreAuthorize("@ss.hasPermi('biz:health:view')")
    @Operation(summary = "历史数据体检报告", description = "重复客户 / 缺料号 / 数量异常 / 税率异常 / 无源出货")
    public R<HealthReportResp> health() {
        return R.ok(reportService.health());
    }

    /**
     * 客户出货汇总。
     *
     * @param begin 下单时间起（yyyy-MM-dd）
     * @param end   下单时间止（yyyy-MM-dd）
     * @return 汇总列表
     */
    @GetMapping("/customer-summary")
    @PreAuthorize("@ss.hasPermi('biz:report:view')")
    @Operation(summary = "客户出货汇总")
    public R<List<CustomerSummaryResp>> customerSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return R.ok(reportService.customerSummary(begin, end));
    }

    /**
     * 催单清单（未交订单明细）。
     *
     * <p>业务员催单按客户要货：行序先客户后下单时间，打印或截图即可去催生产、回复客户。
     * 权限复用报表查询，不新增菜单。
     *
     * @return 未交明细列表
     */
    @GetMapping("/open-items")
    @PreAuthorize("@ss.hasPermi('biz:report:view')")
    @Operation(summary = "催单清单", description = "全部未交订单明细（qtyOpen > 0 且未取消），按客户聚合排序")
    public R<List<OpenOrderItemResp>> openItems() {
        return R.ok(reportService.openItems());
    }
}
