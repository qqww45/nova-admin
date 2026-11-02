package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 换取令牌响应。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdBindingRedeemResp", description = "机器令牌")
public class KdBindingRedeemResp {

    @Schema(description = "机器令牌（仅本次返回，请用 safeStorage 加密保存）")
    private String token;

    @Schema(description = "绑定 ID")
    private String bindingId;

    @Schema(description = "nova 用户 ID")
    private String userId;

    @Schema(description = "账套 ID")
    private String acctId;

    @Schema(description = "账套名称")
    private String acctName;

    @Schema(description = "客户水位（客户端据此做增量）")
    private String watermarkCustomer;

    @Schema(description = "出库水位")
    private String watermarkOutbound;
}
