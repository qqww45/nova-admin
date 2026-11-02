package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下发给采集器的指令。
 *
 * <p>生命周期：{@code PENDING}（待取）→ {@code SENT}（已取走）→ {@code DONE}；
 * 超过 1 小时没被取走则 {@code EXPIRED}，避免"上线瞬间补一次两个月前的同步"。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdCommand {

    /** 待取 */
    public static final String PENDING = "PENDING";

    /** 已被采集器取走 */
    public static final String SENT = "SENT";

    /** 执行完成（成功或失败都算，看 result） */
    public static final String DONE = "DONE";

    /** 过期未取 */
    public static final String EXPIRED = "EXPIRED";

    /** 立即同步 */
    public static final String CMD_SYNC_NOW = "sync.now";

    /** 重载服务端配置（任务清单 / 字段映射） */
    public static final String CMD_CONFIG_RELOAD = "config.reload";

    /** 吊销本地令牌 */
    public static final String CMD_TOKEN_REVOKE = "token.revoke";

    /** 触发客户端升级检查 */
    public static final String CMD_CLIENT_UPDATE = "client.update";

    private Long id;

    private Long bindingId;

    private Long userId;

    private String acctId;

    /** 指令名，见常量 */
    private String command;

    /** 指令参数（JSON 字符串，可为空） */
    private String payload;

    private String status;

    /** 执行结果摘要 */
    private String result;

    private LocalDateTime createTime;

    private LocalDateTime deliverTime;

    private LocalDateTime doneTime;

    private LocalDateTime expireTime;
}
