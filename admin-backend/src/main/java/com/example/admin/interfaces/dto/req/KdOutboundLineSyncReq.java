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
 * 销售出库单分录批量上报请求。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdOutboundLineSyncReq", description = "出库分录批量上报")
public class KdOutboundLineSyncReq {

    @NotBlank(message = "批次号不能为空")
    @Size(max = 64)
    @Schema(description = "批次号")
    private String batchId;

    @Schema(description = "本次终点水位")
    private LocalDateTime watermarkTo;

    @Valid
    @NotNull(message = "rows 不能为空")
    @Size(max = 2000, message = "单次上报不超过 2000 行")
    @Schema(description = "分录行")
    private List<Row> rows = new ArrayList<>();

    /**
     * 分录行。
     */
    @Data
    @Schema(name = "KdOutboundLineSyncRow", description = "出库分录行")
    public static class Row {

        @NotNull(message = "outboundFid 不能为空")
        @Schema(description = "金蝶出库单 FID")
        private Long outboundFid;

        @Schema(description = "金蝶分录内码；为空时服务端按行序号生成稳定键")
        private Long entryId;

        @Size(max = 100)
        @Schema(description = "物料编码")
        private String materialNumber;

        @Size(max = 200)
        @Schema(description = "物料名称")
        private String materialName;

        // 金蝶 FSpecification 是可配置长度的文本字段，真实账套存在超 300 字的规格
        // （2026-11-03 生产批次整批失败：rows[472].spec 超 300）。上限放宽到 1000，
        // 与 kd_outbound_line.spec 列宽（V22）保持一致；禁止截断——规格是对账依据。
        @Size(max = 1000, message = "规格长度不能超过 1000 字")
        @Schema(description = "规格")
        private String spec;

        @Size(max = 20)
        @Schema(description = "单位")
        private String unit;

        @Schema(description = "数量 × 1000")
        private Long qtyMilli;

        @Schema(description = "单价 × 100")
        private Long priceCents;

        @Schema(description = "金额 × 100")
        private Long amountCents;

        @Size(max = 100)
        @Schema(description = "源单号")
        private String srcOrderNo;

        @Size(max = 100)
        @Schema(description = "客户采购订单号")
        private String customerPo;

        @Size(max = 500)
        @Schema(description = "行备注")
        private String remark;
    }
}
