package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户期初应收保存请求。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@Schema(name = "CustomerOpeningSaveReq", description = "客户期初应收保存请求")
public class CustomerOpeningSaveReq {

    /** 客户 ID */
    @NotNull(message = "客户不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    /** 期初基准日 */
    @Schema(description = "期初基准日")
    private LocalDateTime asOfDate;

    /** 期初应收 */
    @NotNull(message = "期初应收不能为空")
    @Schema(description = "期初应收", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal openingBalance;

    /** 币种 */
    @Schema(description = "币种")
    private String currency;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
