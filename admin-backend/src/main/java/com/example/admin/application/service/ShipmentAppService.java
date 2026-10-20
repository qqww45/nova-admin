package com.example.admin.application.service;

import com.example.admin.common.result.PageResult;
import com.example.admin.interfaces.dto.req.ShipmentPageQuery;
import com.example.admin.interfaces.dto.req.ShipmentSaveReq;
import com.example.admin.interfaces.dto.resp.ShipmentBatchResultResp;
import com.example.admin.interfaces.dto.resp.ShipmentResp;

import java.util.List;

/**
 * 出货应用服务：批量录入、作废回写、异常池。
 *
 * <p>界面录入、粘贴、导入三种入口最终都汇聚到 {@code create}，保证校验口径一致。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ShipmentAppService {

    /**
     * 分页查询出货记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ShipmentResp> page(ShipmentPageQuery query);

    /**
     * 查询出货详情。
     *
     * @param id 出货 ID
     * @return 出货记录
     */
    ShipmentResp detail(String id);

    /**
     * 批量新增出货（逐行校验，能过的先过，失败行原样带回原因）。
     *
     * @param req 批量请求
     * @return 逐行结果
     */
    ShipmentBatchResultResp create(ShipmentSaveReq req);

    /**
     * 作废出货（回写扣减明细出货数）。
     *
     * @param id     出货 ID
     * @param reason 作废原因
     */
    void voidShipment(String id, String reason);

    /**
     * 查询某订单明细下的出货流水。
     *
     * @param itemId 明细 ID
     * @return 出货记录
     */
    List<ShipmentResp> listByItem(String itemId);

    /**
     * 今日已录条数（出货页常驻展示）。
     *
     * @return 条数
     */
    long countToday();

    /**
     * 无源出货补挂订单明细（异常数据池的修复动作）。
     *
     * <p>补挂后会按普通出货的同一套规则回写明细出货数并重新计算金额快照，
     * 不会出现"出货有记录、明细仍显示未出货"的对不上账。
     *
     * @param id          出货 ID
     * @param orderItemId 订单明细 ID
     */
    void linkToItem(String id, String orderItemId);
}
