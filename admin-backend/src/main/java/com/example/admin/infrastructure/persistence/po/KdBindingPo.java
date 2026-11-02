package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集器绑定持久化对象（对应 kd_binding）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_binding")
@Schema(name = "KdBindingPo", description = "采集器绑定")
public class KdBindingPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "nova 用户ID")
    private Long userId;

    @Schema(description = "金蝶账套ID")
    private String acctId;

    @Schema(description = "账套名称")
    private String acctName;

    @Schema(description = "金蝶登录账号")
    private String kdUsername;

    @Schema(description = "金蝶服务器地址")
    private String kdServerUrl;

    @Schema(description = "客户端机器名")
    private String machineName;

    @Schema(description = "客户端版本")
    private String clientVersion;

    @Schema(description = "令牌摘要")
    private String tokenHash;

    @Schema(description = "令牌前缀")
    private String tokenPrefix;

    @Schema(description = "状态")
    private String status;

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

    @Version
    @Schema(description = "版本号")
    private Integer version;
}
