package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 需要填写原因的敏感操作请求（作废出货 / 回滚导入 / 取消订单）。
 *
 * <p>[待确认] 是否上审批流 —— 当前按"二次确认 + 填原因，不上审批流"落地。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ReasonReq", description = "敏感操作原因")
public class ReasonReq {

    /** 操作原因 */
    @NotBlank(message = "请填写操作原因")
    @Schema(description = "操作原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
