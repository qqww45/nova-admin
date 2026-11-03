package com.example.admin.interfaces.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 催单清单行（未交订单明细）。
 *
 * <p>业务员催单的真实动作是"按客户要货"：对着一个客户问"我下的单哪些还没交"。
 * 每行是仍处于未交状态的订单明细（{@code qtyOpen > 0} 且未取消），
 * 打印或截图即可去催生产、回复客户。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
public class OpenOrderItemResp {

    /** 订单 ID（ID 一律字符串，防前端精度丢失） */
    private String orderId;

    /** 订单号 */
    private String orderNo;

    /** 客户名称 */
    private String customerName;

    /** 客户订单号（客户 PO） */
    private String customerOrderNo;

    /** 业务员 */
    private String salesmanName;

    /** 下单时间 */
    private LocalDateTime orderDate;

    /** 预计交货时间 */
    private LocalDateTime expectedDeliveryDate;

    /** 型号 */
    private String model;

    /** 料号 */
    private String materialNo;

    /** 规格 */
    private String spec;

    /** 下单数量 */
    private BigDecimal qtyOrdered;

    /** 已出货数量 */
    private BigDecimal qtyShipped;

    /** 未出货数量 */
    private BigDecimal qtyOpen;
}
