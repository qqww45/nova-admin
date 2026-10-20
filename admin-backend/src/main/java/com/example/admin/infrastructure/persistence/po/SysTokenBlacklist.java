package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 令牌黑名单实体（对应表 {@code sys_token_blacklist}）。
 *
 * <p>设计原因：JWT 是无状态的，服务端默认无法主动让其失效。
 * 登出、刷新令牌、强制下线等场景需要将令牌的 {@code jti} 写入黑名单，
 * 认证过滤器校验令牌时会先查黑名单，从而实现“令牌可失效”。
 *
 * <p>实现选择：使用表存储而非纯内存，保证应用重启后黑名单依然有效；
 * 生产环境可替换为 Redis（设置与令牌剩余有效期相同的 TTL，自动过期）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@TableName("sys_token_blacklist")
@Schema(name = "SysTokenBlacklist", description = "令牌黑名单")
public class SysTokenBlacklist implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 令牌唯一标识（JWT 的 jti 声明） */
    @Schema(description = "令牌唯一标识 jti")
    private String jti;

    /** 令牌类型：access / refresh */
    @Schema(description = "令牌类型")
    private String tokenType;

    /** 关联用户 ID */
    @Schema(description = "用户ID")
    private Long userId;

    /** 令牌过期时间（用于定时清理历史数据） */
    @Schema(description = "令牌过期时间")
    private LocalDateTime expireTime;

    /** 加入黑名单的时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
