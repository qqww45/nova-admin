package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 金蝶出库分录数据响应（排查页）。
 *
 * <p>金额单位已换算为元（2 位）、数量为原始精度（3 位），
 * 与对账单明细的口径一致；billNo 由服务端按「账套 + FID」回填。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@Schema(name = "KdOutboundLineDataResp", description = "金蝶出库分录数据")
public class KdOutboundLineDataResp {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "数据归属人")
    private String ownerUserId;

    @Schema(description = "数据归属人姓名（管理员排查时辨认业务员）")
    private String ownerUserName;

    @Schema(description = "账套")
    private String acctId;

    @Schema(description = "金蝶出库单 FID")
    private String outboundFid;

    @Schema(description = "出库单编号（服务端按账套 + FID 回填）")
    private String billNo;

    @Schema(description = "金蝶分录内码（或客户端生成的稳定键）")
    private String entryId;

    @Schema(description = "物料编码")
    private String materialNumber;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "数量")
    private BigDecimal qty;

    @Schema(description = "单价（元）")
    private BigDecimal price;

    @Schema(description = "金额（元）")
    private BigDecimal amount;

    @Schema(description = "源单号（销售订单号）")
    private String srcOrderNo;

    @Schema(description = "客户采购订单号")
    private String customerPo;

    @Schema(description = "行备注")
    private String remark;

    @Schema(description = "是否失效：1 是 0 否")
    private Integer invalid;

    @Schema(description = "最近上报批次")
    private String batchNo;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncAt;
}
