package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.CustomerOpeningSaveReq;
import com.example.admin.interfaces.dto.req.ReceiptPageQuery;
import com.example.admin.interfaces.dto.req.ReceiptSaveReq;
import com.example.admin.interfaces.dto.resp.CustomerOpeningResp;
import com.example.admin.interfaces.dto.resp.ReceiptResp;

/**
 * 收款登记应用服务（客户应收账款回款）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
public interface ReceiptAppService {

    /** 分页查询收款记录 */
    PageResult<ReceiptResp> page(ReceiptPageQuery query);

    /** 查询收款详情 */
    ReceiptResp detail(String id);

    /** 新增或更新收款登记（有 id 视为更新） */
    String save(ReceiptSaveReq req);

    /** 作废收款（红冲，回写审计） */
    void voidReceipt(String id, String reason);

    /** 查询客户期初应收（无则返回 null） */
    CustomerOpeningResp getOpening(String customerId);

    /** 保存客户期初应收（存在则更新） */
    void saveOpening(CustomerOpeningSaveReq req);
}
