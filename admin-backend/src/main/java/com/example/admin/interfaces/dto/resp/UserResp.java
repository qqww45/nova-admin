package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象（列表/详情返回）。
 *
 * <p>安全：不含 password 字段；手机号与邮箱在 Service 层已脱敏。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "UserResp", description = "用户视图对象")
public class UserResp {

    /** 用户 ID（字符串，避免前端精度丢失） */
    @Schema(description = "用户ID")
    private String id;

    /** 用户名 */
    @Schema(description = "用户名")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 邮箱（脱敏） */
    @Schema(description = "邮箱（脱敏）")
    private String email;

    /** 手机号（列表脱敏；编辑详情接口返回明文供回填） */
    @Schema(description = "手机号（列表脱敏；编辑详情返回明文）")
    private String phone;

    /** 性别：0 未知 1 男 2 女 */
    @Schema(description = "性别")
    private Integer sex;

    /** 头像 */
    @Schema(description = "头像")
    private String avatar;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 主题偏好 */
    @Schema(description = "主题偏好")
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

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 角色 ID 集合（编辑时回显） */
    @Schema(description = "角色ID集合")
    private List<String> roleIds;

    /** 角色名称集合（列表展示） */
    @Schema(description = "角色名称集合")
    private List<String> roleNames;
}
