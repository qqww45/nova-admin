package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 金蝶出库单分录持久化对象（对应 kd_outbound_line）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_outbound_line")
@Schema(name = "KdOutboundLinePo", description = "金蝶出库单分录")
public class KdOutboundLinePo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "数据归属人")
    private Long ownerUserId;

    @Schema(description = "金蝶账套")
    private String acctId;

    @Schema(description = "金蝶出库单 FID")
    private Long outboundFid;

    @Schema(description = "金蝶分录内码")
    private Long entryId;

    @Schema(description = "物料编码")
    private String materialNumber;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "规格")
    private String spec;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "数量×1000")
    private Long qtyMilli;

    @Schema(description = "单价×100")
    private Long priceCents;

    @Schema(description = "金额×100")
    private Long amountCents;

    @Schema(description = "源单号")
    private String srcOrderNo;

    @Schema(description = "客户采购订单号")
    private String customerPo;

    @Schema(description = "行备注")
    private String remark;

    @Schema(description = "是否失效")
    private Integer invalid;

    @Schema(description = "最近批次")
    private String batchNo;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncAt;
}
