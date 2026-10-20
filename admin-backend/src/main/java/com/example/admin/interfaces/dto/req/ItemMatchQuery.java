package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 出货自动带出的匹配条件（附录A A.4 三级优先级）。
 *
 * <p>为什么把匹配条件做成一个对象而不是四个 @RequestParam：
 * 这三个字段是"同一行的上下文"，一起变、一起用；
 * 拆成四个可选参数后，调用方很难说清"哪几个字段的组合代表优先级几"。
 *
 * <p>匹配优先级（命中即止）：
 * <ol>
 *   <li>客户订单号 + 客户物料号</li>
 *   <li>客户订单号 + 型号</li>
 *   <li>客户 + 料号</li>
 * </ol>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ItemMatchQuery", description = "出货自动带出匹配条件")
public class ItemMatchQuery {

    @Schema(description = "客户订单号（客户 PO 号）")
    private String customerOrderNo;

    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    @Schema(description = "型号")
    private String model;

    @Schema(description = "料号")
    private String materialNo;

    @Schema(description = "客户ID")
    private String customerId;
}
