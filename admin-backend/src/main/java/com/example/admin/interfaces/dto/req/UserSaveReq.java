package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改请求。
 *
 * <p>约定：{@code id} 为空表示新增（此时密码必填），非空表示修改（密码留空表示不修改）。
 * 该“新增/修改差异化必填”属于业务规则，由 Service 校验，DTO 只做通用格式校验。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "UserSaveReq", description = "用户新增/修改请求")
public class UserSaveReq {

    /** 用户 ID（新增时为空） */
    @Schema(description = "用户ID，新增时为空")
    private String id;

    /** 用户名 */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan")
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]{2,64}$", message = "用户名只能包含字母、数字、下划线、点、短横线，长度 2-64")
    private String username;

    /** 密码：新增必填，修改时留空表示不修改 */
    @Schema(description = "密码：新增必填，修改时留空表示不修改")
    @Size(min = 5, max = 64, message = "密码长度必须在 5 到 64 之间")
    private String password;

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

    /** 头像地址 */
    @Schema(description = "头像地址")
    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 部门 ID */
    @Schema(description = "部门ID")
    private String deptId;

    /** 备注 */
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 角色 ID 集合 */
    @Schema(description = "角色ID集合")
    private List<String> roleIds;
}
