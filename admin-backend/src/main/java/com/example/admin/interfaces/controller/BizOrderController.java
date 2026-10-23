package com.example.admin.interfaces.controller;

import com.example.admin.application.service.OrderAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.OrderPageQuery;
import com.example.admin.interfaces.dto.req.OrderSaveReq;
import com.example.admin.interfaces.dto.req.ReasonReq;
import com.example.admin.interfaces.dto.req.ItemMatchQuery;
import com.example.admin.interfaces.dto.resp.OrderDetailResp;
import com.example.admin.interfaces.dto.resp.OrderItemResp;
import com.example.admin.interfaces.dto.resp.OrderResp;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 订单管理控制器：订单头 + 明细的整单保存、状态流转与对账。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/order")
@Tag(name = "订单管理", description = "订单头与明细、状态流转、对账")
public class BizOrderController {

    private final OrderAppService orderService;

    /**
     * 分页查询订单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('biz:order:list')")
    @Operation(summary = "分页查询订单")
    public R<PageResult<OrderResp>> list(@Valid OrderPageQuery query) {
        return R.ok(orderService.page(query));
    }

    /**
     * 查询订单详情（含明细）。
     *
     * @param id 订单 ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('biz:order:query')")
    @Operation(summary = "查询订单详情")
    public R<OrderDetailResp> detail(@PathVariable String id) {
        return R.ok(orderService.detail(id));
    }

    /**
     * 生成订单号（新建页预览用）。
     *
     * @return 订单号
     */
    @GetMapping("/order-no")
    @PreAuthorize("@ss.hasPermi('biz:order:add')")
    @Operation(summary = "生成订单号")
    public R<String> generateOrderNo() {
        return R.ok(orderService.generateOrderNo());
    }

    /**
     * 新增订单。
     *
     * @param req 订单参数（含明细）
     * @return 新订单 ID
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('biz:order:add')")
    @Operation(summary = "新增订单")
    public R<String> create(@Valid @RequestBody OrderSaveReq req) {
        return R.ok(orderService.create(req));
    }

    /**
     * 整单保存（头 + 明细）。
     *
     * @param req 订单参数（含明细）
     * @return 操作结果
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('biz:order:edit')")
    @Operation(summary = "整单保存订单")
    public R<Void> update(@Valid @RequestBody OrderSaveReq req) {
        orderService.update(req);
        return R.ok();
    }

    /**
     * 删除订单（已有出货时禁止）。
     *
     * @param ids 订单 ID，逗号分隔
     * @return 操作结果
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('biz:order:remove')")
    @Operation(summary = "删除订单")
    public R<Void> remove(@PathVariable String ids) {
        orderService.remove(Arrays.asList(ids.split(",")));
        return R.ok();
    }

    /**
     * 出货自动带出：按 A.4 三级优先级反查候选明细（含未出货数）。
     *
     * @param query 匹配条件
     * @return 候选明细
     */
    @PostMapping("/items/match")
    @PreAuthorize("@ss.hasPermi('biz:shipment:list')")
    @Operation(summary = "出货自动带出候选明细")
    public R<List<OrderItemResp>> matchItems(@RequestBody ItemMatchQuery query) {
        return R.ok(orderService.matchItems(query));
    }

    /**
     * 确认订单。
     *
     * @param id 订单 ID
     * @return 操作结果
     */
    @PutMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPermi('biz:order:confirm')")
    @Operation(summary = "确认订单")
    public R<Void> confirm(@PathVariable String id) {
        orderService.confirm(id);
        return R.ok();
    }

    /**
     * 取消订单（须先作废其下出货）。
     *
     * @param id  订单 ID
     * @param req 取消原因
     * @return 操作结果
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermi('biz:order:cancel')")
    @Operation(summary = "取消订单")
    public R<Void> cancel(@PathVariable String id, @Valid @RequestBody ReasonReq req) {
        orderService.cancel(id, req.getReason());
        return R.ok();
    }

    /**
     * 明细对账（传 date 为空表示撤销对账）。
     *
     * @param itemIds 明细 ID，逗号分隔
     * @param date    对账日期
     * @return 操作结果
     */
    @PutMapping("/reconcile")
    @PreAuthorize("@ss.hasPermi('biz:order:reconcile')")
    @Operation(summary = "明细对账")
    public R<Void> reconcile(@RequestParam String itemIds,
                             @RequestParam(required = false) LocalDate date) {
        // date 按 LocalDate 接收：调用方（前端日期选择器）传的是 "2026-10-20" 这类日期串，
        // 用 LocalDateTime 直接绑会因缺时间部分报"日期格式不正确"；这里统一取当天零点
        orderService.reconcile(Arrays.asList(itemIds.split(",")), date == null ? null : date.atStartOfDay());
        return R.ok();
    }
}
