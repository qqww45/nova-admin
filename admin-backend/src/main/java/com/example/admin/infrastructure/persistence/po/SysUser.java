package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户实体（对应表 {@code sys_user}）。
 *
 * <p>安全说明：
 * <ul>
 *     <li>{@code password} 使用 {@link JsonProperty.Access#WRITE_ONLY}，
 *         保证无论何种序列化路径都不会把密码输出到响应体（双保险，正常应通过 DTO 返回）；</li>
 *     <li>对外返回一律使用 {@code UserResp} 视图对象，并做手机号/邮箱脱敏。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@Schema(name = "SysUser", description = "系统用户")
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户名（登录账号） */
    @Schema(description = "用户名")
    private String username;

    /** 密码（BCrypt 摘要，禁止明文/MD5/SHA-1） */
    @Schema(description = "密码，BCrypt 摘要，仅写入不输出")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 用户昵称 */
    @Schema(description = "用户昵称")
    private String nickname;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;

    /** 性别：0 未知 1 男 2 女 */
    @Schema(description = "性别：0未知 1男 2女")
    private Integer sex;

    /** 头像地址 */
    @Schema(description = "头像地址")
    private String avatar;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 界面主题偏好：light / dark / auto（跨设备同步） */
    @Schema(description = "界面主题偏好：light/dark/auto")
    private String theme;

    /** 所属部门 ID（逻辑外键） */
    @Schema(description = "所属部门ID")
    private Long deptId;

    /** 最后登录 IP */
    @Schema(description = "最后登录IP")
    private String loginIp;

    /** 最后登录时间 */
    @Schema(description = "最后登录时间")
    private LocalDateTime loginDate;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 角色 ID 集合（非数据库字段，用于新增/编辑时绑定角色） */
    @Schema(description = "角色ID集合（非数据库字段）")
    @TableField(exist = false)
    private List<Long> roleIds;

    /** 部门名称（非数据库字段，列表展示用，由 Service 填充） */
    @Schema(description = "部门名称（非数据库字段）")
    @TableField(exist = false)
    private String deptName;
}
