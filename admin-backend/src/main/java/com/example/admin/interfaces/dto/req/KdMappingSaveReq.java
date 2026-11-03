package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存金蝶客户 ↔ 内部客户映射请求。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@Schema(name = "KdMappingSaveReq", description = "保存客户映射")
public class KdMappingSaveReq {

    @Schema(description = "账套 ID")
    @NotBlank(message = "账套不能为空")
    private String acctId;

    @Schema(description = "金蝶客户内码")
    @NotNull(message = "金蝶客户内码不能为空")
    private Long kdFid;

    @Schema(description = "内部客户 ID")
    @NotNull(message = "内部客户不能为空")
    private Long bizCustomerId;

    @Schema(description = "数据归属人 ID（仅管理员代维护时生效；业务员忽略此字段）")
    private String ownerUserId;
}
