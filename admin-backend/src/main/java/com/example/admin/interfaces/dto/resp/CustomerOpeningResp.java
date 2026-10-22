package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户期初应收响应。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "CustomerOpeningResp", description = "客户期初应收")
public class CustomerOpeningResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "期初基准日")
    private LocalDateTime asOfDate;

    @Schema(description = "期初应收")
    private BigDecimal openingBalance;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "备注")
    private String remark;
}
