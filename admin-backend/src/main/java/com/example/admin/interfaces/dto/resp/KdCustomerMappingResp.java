package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金蝶客户 ↔ 内部客户映射行。
 *
 * <p>一行 = 一个金蝶客户 + 它当前的映射状态；业务员在页面逐行确认。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@Schema(name = "KdCustomerMappingResp", description = "金蝶客户映射行")
public class KdCustomerMappingResp {

    @Schema(description = "kd_customer 主键")
    private String id;

    @Schema(description = "账套 ID")
    private String acctId;

    @Schema(description = "金蝶客户内码")
    private String kdFid;

    @Schema(description = "金蝶客户编码")
    private String kdNumber;

    @Schema(description = "金蝶客户名称")
    private String kdName;

    @Schema(description = "内部客户 ID（未匹配为 null）")
    private String bizCustomerId;

    @Schema(description = "内部客户名称（未匹配为 null；内部客户被删时为\"内部客户不存在\"）")
    private String bizCustomerName;

    @Schema(description = "匹配类型：AUTO_SUGGEST / MANUAL；未匹配为 null")
    private String matchType;

    @Schema(description = "状态：UNMATCHED 未匹配 / SUGGESTED 有建议待确认 / CONFIRMED 已确认")
    private String status;

    @Schema(description = "最近映射更新时间")
    private LocalDateTime updateTime;
}
