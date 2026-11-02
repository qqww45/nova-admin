package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户端用绑定码换取机器令牌的请求。
 *
 * <p>金蝶密码不上行，这里只带账套与账号（仅展示）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdBindingRedeemReq", description = "绑定码换取令牌")
public class KdBindingRedeemReq {

    @NotBlank(message = "绑定码不能为空")
    @Size(max = 32, message = "绑定码长度不正确")
    @Schema(description = "一次性绑定码")
    private String code;

    @NotBlank(message = "账套不能为空")
    @Size(max = 64, message = "账套 ID 过长")
    @Schema(description = "金蝶账套 ID")
    private String acctId;

    @Size(max = 200)
    @Schema(description = "账套名称")
    private String acctName;

    @Size(max = 100)
    @Schema(description = "金蝶登录账号")
    private String kdUsername;

    @Size(max = 300)
    @Schema(description = "金蝶服务器地址")
    private String kdServerUrl;

    @Size(max = 200)
    @Schema(description = "客户端机器名")
    private String machineName;

    @Size(max = 50)
    @Schema(description = "客户端版本")
    private String clientVersion;
}
