package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 补挂订单明细的请求（异常数据池修复）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "LinkItemReq", description = "补挂订单明细请求")
public class LinkItemReq {

    /** 目标订单明细 ID */
    @Schema(description = "订单明细ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择要关联的订单明细")
    private String orderItemId;
}
