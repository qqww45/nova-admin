package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.CustomerContactReq;
import com.example.admin.interfaces.dto.req.CustomerInvoiceReq;
import com.example.admin.interfaces.dto.req.CustomerPageQuery;
import com.example.admin.interfaces.dto.req.CustomerSaveReq;
import com.example.admin.interfaces.dto.resp.CustomerDetailResp;
import com.example.admin.interfaces.dto.resp.CustomerResp;

import java.util.List;

/**
 * 客户应用服务：主数据 + 联系人 / 开票信息子表（含默认项规则）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface CustomerAppService {

    /**
     * 分页查询客户。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CustomerResp> page(CustomerPageQuery query);

    /**
     * 查询客户详情（含子表与关联订单）。
     *
     * @param id 客户 ID
     * @return 详情
     */
    CustomerDetailResp detail(String id);

    /**
     * 新增客户。
     *
     * @param req 保存请求
     * @return 新客户 ID
     */
    String create(CustomerSaveReq req);

    /**
     * 修改客户。
     *
     * @param req 保存请求
     */
    void update(CustomerSaveReq req);

    /**
     * 删除客户（有订单时禁止，只允许停用）。
     *
     * @param ids 客户 ID 列表
     */
    void remove(List<String> ids);

    /**
     * 按名称模糊搜索（下拉与导入匹配用）。
     *
     * @param keyword 关键字
     * @param limit   条数上限
     * @return 客户列表
     */
    List<CustomerResp> search(String keyword, int limit);

    /* ---------------- 联系人 ---------------- */

    /**
     * 新增联系人。
     *
     * @param req 联系人
     * @return 新 ID
     */
    String saveContact(CustomerContactReq req);

    /**
     * 删除联系人。
     *
     * @param id 联系人 ID
     */
    void removeContact(String id);

    /**
     * 设为默认联系人。
     *
     * @param id 联系人 ID
     */
    void setDefaultContact(String id);

    /* ---------------- 开票信息 ---------------- */

    /**
     * 新增或修改开票信息。
     *
     * @param req 开票信息
     * @return ID
     */
    String saveInvoice(CustomerInvoiceReq req);

    /**
     * 删除开票信息。
     *
     * @param id 开票信息 ID
     */
    void removeInvoice(String id);

    /**
     * 设为默认开票信息。
     *
     * @param id 开票信息 ID
     */
    void setDefaultInvoice(String id);
}
