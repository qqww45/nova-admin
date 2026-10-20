package com.example.admin.interfaces.controller;

import com.example.admin.application.service.ShipmentAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.LinkItemReq;
import com.example.admin.interfaces.dto.req.ReasonReq;
import com.example.admin.interfaces.dto.req.ShipmentPageQuery;
import com.example.admin.interfaces.dto.req.ShipmentSaveReq;
import com.example.admin.interfaces.dto.resp.ShipmentBatchResultResp;
import com.example.admin.interfaces.dto.resp.ShipmentResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单出货控制器（高频页：界面录入 / 粘贴 / 导入三种入口统一走批量保存）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/shipment")
@Tag(name = "订单出货", description = "出货录入、作废、异常池")
public class BizShipmentController {

    private final ShipmentAppService shipmentService;

    /**
     * 分页查询出货记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('biz:shipment:list')")
    @Operation(summary = "分页查询出货记录")
    public R<PageResult<ShipmentResp>> list(@Valid ShipmentPageQuery query) {
        return R.ok(shipmentService.page(query));
    }

    /**
     * 今日已录条数（页面常驻展示）。
     *
     * @return 条数
     */
    @GetMapping("/today-count")
    @Operation(summary = "今日已录条数")
    public R<Long> todayCount() {
        return R.ok(shipmentService.countToday());
    }

    /**
     * 查询出货详情。
     *
     * @param id 出货 ID
     * @return 出货记录
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询出货详情")
    public R<ShipmentResp> detail(@PathVariable String id) {
        return R.ok(shipmentService.detail(id));
    }

    /**
     * 批量新增出货（界面录入 / 粘贴 / 导入共用，返回逐行结果）。
     *
     * @param req 批量请求
     * @return 逐行结果
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('biz:shipment:add')")
    @Operation(summary = "批量新增出货")
    public R<ShipmentBatchResultResp> create(@Valid @RequestBody ShipmentSaveReq req) {
        return R.ok(shipmentService.create(req));
    }

    /**
     * 作废出货（敏感操作，需填原因）。
     *
     * @param id  出货 ID
     * @param req 作废原因
     * @return 操作结果
     */
    @PutMapping("/{id}/void")
    @PreAuthorize("@ss.hasPermi('biz:shipment:void')")
    @Operation(summary = "作废出货")
    public R<Void> voidShipment(@PathVariable String id, @Valid @RequestBody ReasonReq req) {
        shipmentService.voidShipment(id, req.getReason());
        return R.ok();
    }

    /**
     * 无源出货补挂订单明细（异常数据池的修复动作）。
     *
     * @param id    出货 ID
     * @param req   目标订单明细
     * @return 操作结果
     */
    @PutMapping("/{id}/link")
    @PreAuthorize("@ss.hasPermi('biz:anomaly:fix')")
    @Operation(summary = "补挂订单明细")
    public R<Void> linkToItem(@PathVariable String id, @Valid @RequestBody LinkItemReq req) {
        shipmentService.linkToItem(id, req.getOrderItemId());
        return R.ok();
    }

    /**
     * 查询某订单明细下的出货流水。
     *
     * @param itemId 明细 ID
     * @return 出货记录
     */
    @GetMapping("/by-item/{itemId}")
    @Operation(summary = "查询明细的出货流水")
    public R<List<ShipmentResp>> listByItem(@PathVariable String itemId) {
        return R.ok(shipmentService.listByItem(itemId));
    }
}
