package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 在线会话响应（一条 = 一个用户的一台设备）。
 *
 * <p>字段取舍：只暴露"管理员处置所需"的信息（谁、在哪台设备、什么时间、来自哪里），
 * 不返回任何令牌内容 —— 展示层拿到令牌没有任何用途，只会扩大泄漏面。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "OnlineSessionResp", description = "在线会话")
public class OnlineSessionResp {

    /** 用户 ID */
    @Schema(description = "用户ID")
    private String userId;

    /** 用户名 */
    @Schema(description = "用户名")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 部门名称 */
    @Schema(description = "部门")
    private String deptName;

    /** 登录 IP */
    @Schema(description = "登录IP")
    private String ip;

    /** 浏览器 */
    @Schema(description = "浏览器")
    private String browser;

    /** 操作系统 */
    @Schema(description = "操作系统")
    private String os;

    /** 设备标识 */
    @Schema(description = "设备标识")
    private String deviceId;

    /** 该设备是否已纳入可信列表 */
    @Schema(description = "设备是否已可信", example = "true")
    private Boolean trusted;

    /** 登录时间 */
    @Schema(description = "登录时间")
    private LocalDateTime loginTime;

    /** 最近活跃时间（最近一次刷新令牌） */
    @Schema(description = "最近活跃时间")
    private LocalDateTime lastAccessTime;
}
