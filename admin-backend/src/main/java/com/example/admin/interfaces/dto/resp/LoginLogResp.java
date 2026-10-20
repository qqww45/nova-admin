package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志视图对象（仪表盘“最近登录记录”）。
 *
 * <p>安全：IP 已做脱敏处理（如 192.168.*.*）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "LoginLogResp", description = "登录日志视图对象")
public class LoginLogResp {

    /** 日志 ID */
    @Schema(description = "日志ID")
    private String id;

    /** 登录账号 */
    @Schema(description = "登录账号")
    private String username;

    /** 登录 IP（脱敏） */
    @Schema(description = "登录IP（脱敏）")
    private String ip;

    /** 登录地点 */
    @Schema(description = "登录地点")
    private String location;

    /** 浏览器 */
    @Schema(description = "浏览器")
    private String browser;

    /** 操作系统 */
    @Schema(description = "操作系统")
    private String os;

    /** 状态：0 成功 1 失败 */
    @Schema(description = "状态：0成功 1失败")
    private Integer status;

    /** 提示信息 */
    @Schema(description = "提示信息")
    private String msg;

    /** 登录时间 */
    @Schema(description = "登录时间")
    private LocalDateTime loginTime;
}
