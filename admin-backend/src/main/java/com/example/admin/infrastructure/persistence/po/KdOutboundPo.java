package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 金蝶销售出库单头持久化对象（对应 kd_outbound）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_outbound")
@Schema(name = "KdOutboundPo", description = "金蝶出库单头")
public class KdOutboundPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "数据归属人")
    private Long ownerUserId;

    @Schema(description = "金蝶账套")
    private String acctId;

    @Schema(description = "金蝶 FID")
    private Long kdFid;

    @Schema(description = "单据编号")
    private String billNo;

    @Schema(description = "业务日期")
    private LocalDateTime bizDate;

    @Schema(description = "金蝶客户内码")
    private Long customerFid;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "单据状态")
    private String docStatus;

    @Schema(description = "作废状态")
    private String cancelStatus;

    @Schema(description = "是否作废")
    private Integer cancelled;

    @Schema(description = "表头金额（分）")
    private Long amountCents;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "金蝶修改时间")
    private LocalDateTime kdModifyTime;

    @Schema(description = "是否失效")
    private Integer invalid;

    @Schema(description = "最近批次")
    private String batchNo;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncAt;
}
