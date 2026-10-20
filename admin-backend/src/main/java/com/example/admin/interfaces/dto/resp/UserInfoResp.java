package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前登录用户信息（{@code /getInfo} 响应）。
 *
 * <p>前端拿到本响应后：
 * <ul>
 *     <li>roles / permissions 存入 Pinia，用于按钮级权限判断（{@code v-hasPermi}）；</li>
 *     <li>theme 用于恢复用户的主题偏好（light / dark / auto）；</li>
 *     <li>user 中的手机号与邮箱已脱敏，可直接展示。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "UserInfoResp", description = "当前用户信息")
public class UserInfoResp {

    /** 用户基本信息 */
    @Schema(description = "用户基本信息")
    private UserInfo user;

    /** 角色标识集合 */
    @Schema(description = "角色标识集合")
    private List<String> roles;

    /** 权限标识集合 */
    @Schema(description = "权限标识集合")
    private List<String> permissions;

    /**
     * 用户基本信息（内嵌结构，避免与实体耦合）。
     */
    @Data
    @Schema(name = "UserInfo", description = "用户基本信息")
    public static class UserInfo {

        /** 用户 ID（字符串，避免前端大整数精度丢失） */
        @Schema(description = "用户ID")
        private String id;

        /** 用户名 */
        @Schema(description = "用户名")
        private String username;

        /** 昵称 */
        @Schema(description = "昵称")
        private String nickname;

        /** 头像 */
        @Schema(description = "头像")
        private String avatar;

        /** 邮箱（已脱敏） */
        @Schema(description = "邮箱（已脱敏）")
        private String email;

        /** 手机号（已脱敏） */
        @Schema(description = "手机号（已脱敏）")
        private String phone;

        /** 性别：0 未知 1 男 2 女 */
        @Schema(description = "性别")
        private Integer sex;

        /** 状态：0 正常 1 停用 */
        @Schema(description = "状态")
        private Integer status;

        /** 主题偏好 */
        @Schema(description = "主题偏好：light/dark/auto")
        private String theme;

        /** 部门 ID */
        @Schema(description = "部门ID")
        private String deptId;

        /** 部门名称 */
        @Schema(description = "部门名称")
        private String deptName;

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
}
