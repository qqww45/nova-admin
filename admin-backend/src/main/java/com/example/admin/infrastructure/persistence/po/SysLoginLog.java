package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志实体（对应表 {@code sys_login_log}）。
 *
 * <p>用于安全审计与仪表盘“最近登录记录”展示。
 * 注意：日志中不得记录密码、令牌等敏感信息。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@TableName("sys_login_log")
@Schema(name = "SysLoginLog", description = "登录日志")
public class SysLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录账号 */
    @Schema(description = "登录账号")
    private String username;

    /** 登录 IP */
    @Schema(description = "登录IP")
    private String ip;

    /** 登录地点（预留，可对接 IP 库） */
    @Schema(description = "登录地点")
    private String location;

    /** 浏览器类型 */
    @Schema(description = "浏览器类型")
    private String browser;

    /** 操作系统 */
    @Schema(description = "操作系统")
    private String os;

    /** 登录状态：0 成功 1 失败 */
    @Schema(description = "登录状态：0成功 1失败")
    private Integer status;

    /** 提示消息 */
    @Schema(description = "提示消息")
    private String msg;

    /** 登录时间 */
    @Schema(description = "登录时间")
    private LocalDateTime loginTime;
}
