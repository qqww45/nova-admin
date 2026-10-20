package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.OrderPageQuery;
import com.example.admin.interfaces.dto.req.OrderSaveReq;
import com.example.admin.interfaces.dto.resp.OrderDetailResp;
import com.example.admin.interfaces.dto.resp.OrderResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单应用服务：订单头 + 明细的整单保存、状态流转、对账。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface OrderAppService {

    /**
     * 分页查询订单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<OrderResp> page(OrderPageQuery query);

    /**
     * 查询订单详情（含明细）。
     *
     * @param id 订单 ID
     * @return 详情
     */
    OrderDetailResp detail(String id);

    /**
     * 新增订单（含明细）。
     *
     * @param req 保存请求
     * @return 新订单 ID
     */
    String create(OrderSaveReq req);

    /**
     * 整单保存（头 + 明细：新增行插入、已有行更新、缺失行删除）。
     *
     * @param req 保存请求
     */
    void update(OrderSaveReq req);

    /**
     * 删除订单（已有出货时禁止）。
     *
     * @param ids 订单 ID 列表
     */
    void remove(List<String> ids);

    /**
     * 确认订单（草稿 → 已确认）。
     *
     * @param id 订单 ID
     */
    void confirm(String id);

    /**
     * 取消订单（须先作废其下全部出货）。
     *
     * @param id     订单 ID
     * @param reason 取消原因
     */
    void cancel(String id, String reason);

    /**
     * 明细对账（填写对账日期即视为已对账）。
     *
     * @param itemId 明细 ID 列表
     * @param date   对账日期，传 null 表示撤销对账
     */
    void reconcile(List<String> itemId, LocalDateTime date);

    /**
     * 生成订单号（新建页可先预览）。
     *
     * @return 订单号
     */
    String generateOrderNo();

    /**
     * 出货自动带出：按附录A A.4 的三级优先级反查候选明细。
     *
     * <p>只返回还有未出货数的明细 —— 已经出完的明细不该再被带出来，
     * 否则用户刚选中就会被"超过未出货数"的校验挡回去，等于白点一次。
     *
     * @param query 匹配条件
     * @return 候选明细列表
     */
    List<com.example.admin.interfaces.dto.resp.OrderItemResp> matchItems(
            com.example.admin.interfaces.dto.req.ItemMatchQuery query);
}
