package com.example.admin.interfaces.controller;

import com.example.admin.application.service.KdDataAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.KdCustomerPageQuery;
import com.example.admin.interfaces.dto.req.KdLinePageQuery;
import com.example.admin.interfaces.dto.req.KdOutboundPageQuery;
import com.example.admin.interfaces.dto.resp.KdCustomerDataResp;
import com.example.admin.interfaces.dto.resp.KdOutboundDataResp;
import com.example.admin.interfaces.dto.resp.KdOutboundLineDataResp;
import com.example.admin.interfaces.dto.resp.KdOwnerResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 金蝶同步数据浏览接口（只读，排查用）。
 *
 * <p>覆盖采集器上报落库的三张核心业务表：客户 / 出库单头 / 出库分录。
 * 与对账单接口的差异：不排除作废 / 失效行、不限查询窗口、支持按批次与
 * 业务员过滤——排查"数据为什么没上来 / 为什么对不上"需要看到原始行。
 *
 * <p>数据隔离（服务层强制）：业务员只能看本人上报的数据；
 * 超级管理员可跨业务员查看并按业务员过滤（方案 §5）。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Tag(name = "金蝶同步数据", description = "采集器上报的客户 / 出库单 / 分录原始数据查询")
@RestController
@RequestMapping("/kd/data")
@Validated
@RequiredArgsConstructor
public class KdDataController {

    private final KdDataAppService dataAppService;

    /**
     * 分页查客户数据。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "客户数据分页", description = "采集器上报的金蝶客户（业务员只见本人）")
    @GetMapping("/customer")
    @PreAuthorize("@ss.hasPermi('kd:customer:list')")
    public R<PageResult<KdCustomerDataResp>> customerPage(@Valid KdCustomerPageQuery query) {
        return R.ok(dataAppService.customerPage(query));
    }

    /**
     * 分页查出库单数据。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "出库单数据分页", description = "采集器上报的金蝶出库单头（含作废 / 失效行）")
    @GetMapping("/outbound")
    @PreAuthorize("@ss.hasPermi('kd:outbound:list')")
    public R<PageResult<KdOutboundDataResp>> outboundPage(@Valid KdOutboundPageQuery query) {
        return R.ok(dataAppService.outboundPage(query));
    }

    /**
     * 取一张出库单的全部分录（列表展开行用）。
     *
     * @param id 出库单主键
     * @return 分录列表
     */
    @Operation(summary = "出库单分录", description = "按单据展开查看全部分录（含失效行）")
    @GetMapping("/outbound/{id}/lines")
    @PreAuthorize("@ss.hasPermi('kd:outbound:list')")
    public R<List<KdOutboundLineDataResp>> outboundLines(@PathVariable String id) {
        return R.ok(dataAppService.outboundLines(id));
    }

    /**
     * 分页查出库分录数据（跨单检索）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "出库分录数据分页", description = "按物料 / 源单号 / 客户PO / 批次跨单检索分录")
    @GetMapping("/line")
    @PreAuthorize("@ss.hasPermi('kd:line:list')")
    public R<PageResult<KdOutboundLineDataResp>> linePage(@Valid KdLinePageQuery query) {
        return R.ok(dataAppService.linePage(query));
    }

    /**
     * 有采集器绑定的业务员列表（管理员筛选下拉用）。
     *
     * @return ID + 姓名
     */
    @Operation(summary = "数据归属人列表", description = "有采集器绑定的业务员（仅管理员）")
    @GetMapping("/owners")
    @PreAuthorize("@ss.hasPermi('kd:outbound:list')")
    public R<List<KdOwnerResp>> owners() {
        return R.ok(dataAppService.owners());
    }
}
