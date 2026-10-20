package com.example.admin.interfaces.controller;

import com.example.admin.application.service.CustomerAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.CustomerContactReq;
import com.example.admin.interfaces.dto.req.CustomerInvoiceReq;
import com.example.admin.interfaces.dto.req.CustomerPageQuery;
import com.example.admin.interfaces.dto.req.CustomerSaveReq;
import com.example.admin.interfaces.dto.resp.CustomerDetailResp;
import com.example.admin.interfaces.dto.resp.CustomerResp;
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

import java.util.Arrays;
import java.util.List;

/**
 * 客户管理控制器：客户主数据 + 联系人 / 开票信息子表。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/biz/customer")
@Tag(name = "客户管理", description = "客户主数据、联系人与开票信息")
public class BizCustomerController {

    private final CustomerAppService customerService;

    /**
     * 分页查询客户。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('biz:customer:list')")
    @Operation(summary = "分页查询客户")
    public R<PageResult<CustomerResp>> list(@Valid CustomerPageQuery query) {
        return R.ok(customerService.page(query));
    }

    /**
     * 查询客户详情。
     *
     * @param id 客户 ID
     * @return 客户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('biz:customer:query')")
    @Operation(summary = "查询客户详情")
    public R<CustomerDetailResp> detail(@PathVariable String id) {
        return R.ok(customerService.detail(id));
    }

    /**
     * 客户下拉搜索（订单/出货页面选客户用）。
     *
     * @param keyword 关键字
     * @param limit   条数上限
     * @return 客户列表
     */
    @GetMapping("/search")
    @Operation(summary = "客户下拉搜索")
    public R<List<CustomerResp>> search(@RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "20") int limit) {
        return R.ok(customerService.search(keyword, limit));
    }

    /**
     * 新增客户。
     *
     * @param req 客户参数
     * @return 新客户 ID
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('biz:customer:add')")
    @Operation(summary = "新增客户")
    public R<String> create(@Valid @RequestBody CustomerSaveReq req) {
        return R.ok(customerService.create(req));
    }

    /**
     * 修改客户。
     *
     * @param req 客户参数
     * @return 操作结果
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('biz:customer:edit')")
    @Operation(summary = "修改客户")
    public R<Void> update(@Valid @RequestBody CustomerSaveReq req) {
        customerService.update(req);
        return R.ok();
    }

    /**
     * 删除客户（有订单时禁止，只允许停用）。
     *
     * @param ids 客户 ID，逗号分隔
     * @return 操作结果
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('biz:customer:remove')")
    @Operation(summary = "删除客户")
    public R<Void> remove(@PathVariable String ids) {
        customerService.remove(Arrays.asList(ids.split(",")));
        return R.ok();
    }

    /* ---------------- 联系人 ---------------- */

    /**
     * 新增或修改联系人。
     *
     * @param req 联系人参数
     * @return 联系人 ID
     */
    @PostMapping("/contact")
    @PreAuthorize("@ss.hasPermi('biz:customer:edit')")
    @Operation(summary = "保存联系人")
    public R<String> saveContact(@Valid @RequestBody CustomerContactReq req) {
        return R.ok(customerService.saveContact(req));
    }

    /**
     * 删除联系人。
     *
     * @param id 联系人 ID
     * @return 操作结果
     */
    @DeleteMapping("/contact/{id}")
    @PreAuthorize("@ss.hasPermi('biz:customer:edit')")
    @Operation(summary = "删除联系人")
    public R<Void> removeContact(@PathVariable String id) {
        customerService.removeContact(id);
        return R.ok();
    }

    /**
     * 设为默认联系人。
     *
     * @param id 联系人 ID
     * @return 操作结果
     */
    @PutMapping("/contact/{id}/default")
    @PreAuthorize("@ss.hasPermi('biz:customer:edit')")
    @Operation(summary = "设为默认联系人")
    public R<Void> setDefaultContact(@PathVariable String id) {
        customerService.setDefaultContact(id);
        return R.ok();
    }

    /* ---------------- 开票信息 ---------------- */

    /**
     * 新增或修改开票信息。
     *
     * @param req 开票信息参数
     * @return 开票信息 ID
     */
    @PostMapping("/invoice")
    @PreAuthorize("@ss.hasPermi('biz:customer:edit')")
    @Operation(summary = "保存开票信息")
    public R<String> saveInvoice(@Valid @RequestBody CustomerInvoiceReq req) {
        return R.ok(customerService.saveInvoice(req));
    }

    /**
     * 删除开票信息。
     *
     * @param id 开票信息 ID
     * @return 操作结果
     */
    @DeleteMapping("/invoice/{id}")
    @PreAuthorize("@ss.hasPermi('biz:customer:edit')")
    @Operation(summary = "删除开票信息")
    public R<Void> removeInvoice(@PathVariable String id) {
        customerService.removeInvoice(id);
        return R.ok();
    }

    /**
     * 设为默认开票信息。
     *
     * @param id 开票信息 ID
     * @return 操作结果
     */
    @PutMapping("/invoice/{id}/default")
    @PreAuthorize("@ss.hasPermi('biz:customer:edit')")
    @Operation(summary = "设为默认开票信息")
    public R<Void> setDefaultInvoice(@PathVariable String id) {
        customerService.setDefaultInvoice(id);
        return R.ok();
    }
}
