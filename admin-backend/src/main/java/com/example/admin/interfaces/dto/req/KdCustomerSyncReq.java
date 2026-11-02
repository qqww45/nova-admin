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
 * 客户批量上报请求。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdCustomerSyncReq", description = "客户批量上报")
public class KdCustomerSyncReq {

    @NotBlank(message = "批次号不能为空")
    @Size(max = 64)
    @Schema(description = "批次号")
    private String batchId;

    @Schema(description = "本次终点水位（服务端取较新值，不会回拨）")
    private LocalDateTime watermarkTo;

    @Valid
    @NotNull(message = "rows 不能为空")
    @Size(max = 2000, message = "单次上报不超过 2000 行")
    @Schema(description = "客户行")
    private List<Row> rows = new ArrayList<>();

    /**
     * 客户行。
     */
    @Data
    @Schema(name = "KdCustomerSyncRow", description = "客户行")
    public static class Row {

        @NotNull(message = "kdFid 不能为空")
        @Schema(description = "金蝶 FCUSTID")
        private Long kdFid;

        @Size(max = 100)
        @Schema(description = "客户编码 FNumber")
        private String number;

        @Size(max = 200)
        @Schema(description = "客户名称 FName")
        private String name;

        @Size(max = 500)
        @Schema(description = "地址 FADDRESS")
        private String address;

        @Schema(description = "是否禁用：1 是 0 否（FForbidStatus = B）")
        private Integer disabled;
    }
}
