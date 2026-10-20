package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心信息（{@code /user/profile} 响应）。
 *
 * <p>相比 {@code UserInfoResp} 更偏“可编辑资料”，包含真实（未脱敏）的邮箱与手机号，
 * 因为用户查看自己的资料时无需脱敏。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ProfileResp", description = "个人中心信息")
public class ProfileResp {

    /** 用户 ID */
    @Schema(description = "用户ID")
    private String id;

    /** 用户名（不可修改） */
    @Schema(description = "用户名")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;

    /** 性别：0 未知 1 男 2 女 */
    @Schema(description = "性别")
    private Integer sex;

    /** 头像 */
    @Schema(description = "头像")
    private String avatar;

    /** 部门名称 */
    @Schema(description = "部门名称")
    private String deptName;

    /** 角色名称集合 */
    @Schema(description = "角色名称集合")
    private List<String> roleNames;

    /** 主题偏好 */
    @Schema(description = "主题偏好：light/dark/auto")
    private String theme;

    /** 最后登录 IP */
    @Schema(description = "最后登录IP")
    private String loginIp;

    /** 最后登录时间 */
    @Schema(description = "最后登录时间")
    private LocalDateTime loginDate;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
