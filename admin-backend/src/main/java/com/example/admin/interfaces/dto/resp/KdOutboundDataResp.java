package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 金蝶出库单数据响应（排查页）。
 *
 * <p>与对账单的差异：不排除作废 / 失效单，并带上分录行数——
 * "该单无分录数据"的单据在对账单里退回表头金额，排查时先看行数。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@Schema(name = "KdOutboundDataResp", description = "金蝶出库单数据")
public class KdOutboundDataResp {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "数据归属人")
    private String ownerUserId;

    @Schema(description = "数据归属人姓名（管理员排查时辨认业务员）")
    private String ownerUserName;

    @Schema(description = "账套")
    private String acctId;

    @Schema(description = "金蝶 FID")
    private String kdFid;

    @Schema(description = "单据编号 FBillNo")
    private String billNo;

    @Schema(description = "业务日期 FDate")
    private LocalDateTime bizDate;

    @Schema(description = "金蝶客户内码")
    private String customerFid;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "单据状态 FDocumentStatus")
    private String docStatus;

    @Schema(description = "单据状态标签")
    private String docStatusLabel;

    @Schema(description = "作废状态 FCancelStatus")
    private String cancelStatus;

    @Schema(description = "是否作废：1 是 0 否")
    private Integer cancelled;

    @Schema(description = "表头金额（元，分录汇总口径）")
    private BigDecimal amount;

    @Schema(description = "分录行数（0 = 该单无分录数据，对账单会退回表头金额）")
    private Integer lineCount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "金蝶修改时间 FModifyDate")
    private LocalDateTime kdModifyTime;

    @Schema(description = "是否失效：1 是 0 否（全量差集标记，金蝶侧已删除）")
    private Integer invalid;

    @Schema(description = "最近上报批次")
    private String batchNo;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncAt;
}
