package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.KdCustomerPageQuery;
import com.example.admin.interfaces.dto.req.KdLinePageQuery;
import com.example.admin.interfaces.dto.req.KdOutboundPageQuery;
import com.example.admin.interfaces.dto.resp.KdCustomerDataResp;
import com.example.admin.interfaces.dto.resp.KdOutboundDataResp;
import com.example.admin.interfaces.dto.resp.KdOutboundLineDataResp;
import com.example.admin.interfaces.dto.resp.KdOwnerResp;

import java.util.List;

/**
 * 金蝶同步数据浏览应用服务（只读，排查用）。
 *
 * <p>数据隔离口径与对账单一致（docs/金蝶对账同步方案.md §5）：
 * 业务员只能看自己上报的数据（owner_user_id = 当前登录用户），
 * 超级管理员可跨业务员查看，并可显式按业务员过滤。
 *
 * @author admin-system
 * @since 2026-11-03
 */
public interface KdDataAppService {

    /**
     * 分页查客户数据。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<KdCustomerDataResp> customerPage(KdCustomerPageQuery query);

    /**
     * 分页查出库单数据（含作废 / 失效行，带分录行数）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<KdOutboundDataResp> outboundPage(KdOutboundPageQuery query);

    /**
     * 取一张出库单的全部分录（含失效行）。
     *
     * @param id 出库单主键
     * @return 分录列表
     */
    List<KdOutboundLineDataResp> outboundLines(String id);

    /**
     * 分页查出库分录数据（跨单检索：物料 / 源单号 / 客户PO / 批次）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<KdOutboundLineDataResp> linePage(KdLinePageQuery query);

    /**
     * 有采集器绑定的业务员列表（管理员排查页筛选下拉用）。
     *
     * @return ID + 姓名
     */
    List<KdOwnerResp> owners();
}
