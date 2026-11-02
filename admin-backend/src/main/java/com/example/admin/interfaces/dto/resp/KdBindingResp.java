package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 绑定列表项。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdBindingResp", description = "采集器绑定")
public class KdBindingResp {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "nova 用户 ID")
    private String userId;

    @Schema(description = "nova 用户名")
    private String userName;

    @Schema(description = "账套 ID")
    private String acctId;

    @Schema(description = "账套名称")
    private String acctName;

    @Schema(description = "金蝶账号")
    private String kdUsername;

    @Schema(description = "金蝶服务器")
    private String kdServerUrl;

    @Schema(description = "机器名")
    private String machineName;

    @Schema(description = "客户端版本")
    private String clientVersion;

    @Schema(description = "令牌前缀")
    private String tokenPrefix;

    @Schema(description = "状态：ACTIVE / REVOKED")
    private String status;

    @Schema(description = "状态标签")
    private String statusLabel;

    @Schema(description = "最近使用时间")
    private LocalDateTime lastUsedAt;

    @Schema(description = "最近同步时间")
    private LocalDateTime lastSyncAt;

    @Schema(description = "最近同步结果")
    private String lastSyncResult;

    @Schema(description = "客户水位")
    private LocalDateTime watermarkCustomer;

    @Schema(description = "出库水位")
    private LocalDateTime watermarkOutbound;

    @Schema(description = "客户数")
    private Long customerCount;

    @Schema(description = "出库单数")
    private Long outboundCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
