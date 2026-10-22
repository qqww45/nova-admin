package com.example.admin.interfaces.controller;

import com.example.admin.application.service.ReceiptAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.CustomerOpeningSaveReq;
import com.example.admin.interfaces.dto.req.ReasonReq;
import com.example.admin.interfaces.dto.req.ReceiptPageQuery;
import com.example.admin.interfaces.dto.req.ReceiptSaveReq;
import com.example.admin.interfaces.dto.resp.CustomerOpeningResp;
import com.example.admin.interfaces.dto.resp.ReceiptResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 收款登记控制器（含客户期初应收管理）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/receipt")
@Tag(name = "收款登记", description = "客户回款登记、作废、期初应收")
public class BizReceiptController {

    private final ReceiptAppService receiptService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('biz:receipt:list')")
    @Operation(summary = "分页查询收款记录")
    public R<PageResult<ReceiptResp>> list(@Valid ReceiptPageQuery query) {
        return R.ok(receiptService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('biz:receipt:list')")
    @Operation(summary = "查询收款详情")
    public R<ReceiptResp> detail(@PathVariable String id) {
        return R.ok(receiptService.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('biz:receipt:add')")
    @Operation(summary = "新增/更新收款登记")
    public R<String> save(@Valid @RequestBody ReceiptSaveReq req) {
        return R.ok(receiptService.save(req));
    }

    @PutMapping("/{id}/void")
    @PreAuthorize("@ss.hasPermi('biz:receipt:void')")
    @Operation(summary = "作废收款")
    public R<Void> voidReceipt(@PathVariable String id, @Valid @RequestBody ReasonReq req) {
        receiptService.voidReceipt(id, req.getReason());
        return R.ok();
    }

    @GetMapping("/opening")
    @PreAuthorize("@ss.hasPermi('biz:receipt:list')")
    @Operation(summary = "查询客户期初应收")
    public R<CustomerOpeningResp> opening(@RequestParam @NotBlank String customerId) {
        return R.ok(receiptService.getOpening(customerId));
    }

    @PutMapping("/opening")
    @PreAuthorize("@ss.hasPermi('biz:receipt:add')")
    @Operation(summary = "保存客户期初应收")
    public R<Void> saveOpening(@Valid @RequestBody CustomerOpeningSaveReq req) {
        receiptService.saveOpening(req);
        return R.ok();
    }
}
