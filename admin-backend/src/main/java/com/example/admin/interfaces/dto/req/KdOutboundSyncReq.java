package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售出库单头批量上报请求。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdOutboundSyncReq", description = "出库单头批量上报")
public class KdOutboundSyncReq {

    @NotBlank(message = "批次号不能为空")
    @Size(max = 64)
    @Schema(description = "批次号")
    private String batchId;

    @Schema(description = "本次终点水位")
    private LocalDateTime watermarkTo;

    @Valid
    @NotNull(message = "rows 不能为空")
    @Size(max = 2000, message = "单次上报不超过 2000 行")
    @Schema(description = "出库单头行")
    private List<Row> rows = new ArrayList<>();

    /**
     * 出库单头行。
     */
    @Data
    @Schema(name = "KdOutboundSyncRow", description = "出库单头行")
    public static class Row {

        @NotNull(message = "fid 不能为空")
        @Schema(description = "金蝶 FID")
        private Long fid;

        @Size(max = 100)
        @Schema(description = "单据编号 FBillNo")
        private String billNo;

        @Schema(description = "业务日期 FDate")
        private LocalDateTime bizDate;

        @Schema(description = "客户内码 FCustomerID")
        private Long customerFid;

        @Size(max = 200)
        @Schema(description = "客户名称")
        private String customerName;

        @Size(max = 20)
        @Schema(description = "单据状态 FDocumentStatus")
        private String docStatus;

        @Size(max = 20)
        @Schema(description = "作废状态 FCancelStatus")
        private String cancelStatus;

        @Schema(description = "头金额（分），服务端会按分录重算")
        private Long amountCents;

        @Size(max = 500)
        @Schema(description = "备注")
        private String remark;

        @Schema(description = "金蝶修改时间 FModifyDate")
        private LocalDateTime modifyTime;
    }
}
