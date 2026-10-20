package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求。
 *
 * <p>注意：不允许修改用户名与角色（需管理员在用户管理页操作），此处仅开放资料字段。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ProfileUpdateReq", description = "修改个人资料请求")
public class ProfileUpdateReq {

    /** 昵称 */
    @Schema(description = "昵称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    /** 邮箱 */
    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 手机号 */
    @Schema(description = "手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 性别：0 未知 1 男 2 女 */
    @Schema(description = "性别：0未知 1男 2女")
    private Integer sex;

    /** 头像 */
    @Schema(description = "头像")
    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
