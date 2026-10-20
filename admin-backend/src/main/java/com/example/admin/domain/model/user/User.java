package com.example.admin.domain.model.user;

import com.example.admin.common.constant.Constants;
import com.example.admin.domain.service.PasswordHasher;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户聚合根（Domain 层核心模型）。
 *
 * <p>设计说明（DDD）：
 * <ul>
 *     <li><b>充血模型</b>：密码修改、启停、分配角色、记录登录等业务规则内聚在聚合根中，
 *         而不是散落在 Service 里做一堆 setter 调用（贫血模型的典型症状）；</li>
 *     <li><b>不依赖任何框架</b>：本类没有 MyBatis-Plus / Spring 注解，
 *         持久化对象（PO）与领域模型的转换由 Infrastructure 层的 Converter 负责；</li>
 *     <li><b>密码以哈希形式存在</b>：字段命名为 passwordHash，且没有 getter 之外的暴露途径，
 *         加密策略通过 {@link PasswordHasher} 领域服务接口注入（策略模式 + 依赖倒置）；</li>
 *     <li><b>不做脱敏</b>：脱敏属于"展示"关注点，由 interfaces 层的 Assembler 负责，
 *         领域模型保持数据完整性（个人中心需要完整手机号）。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class User {

    /** 主键（雪花算法生成，非数据库自增） */
    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** 密码哈希（BCrypt），任何时刻都不保存明文 */
    private String passwordHash;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 性别：0 未知 1 男 2 女 */
    private Integer sex;

    /** 头像地址 */
    private String avatar;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 主题偏好：light / dark / auto（跨设备同步的持久化配置） */
    private String theme;

    /** 所属部门 ID */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 最后登录 IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 角色 ID 集合（聚合内维护用户-角色关联，保证聚合一致性） */
    private List<Long> roleIds = new ArrayList<>();

    /**
     * 判断账号是否处于可用状态。
     *
     * @return 状态为正常（0）返回 true
     */
    public boolean isEnabled() {
        return Constants.STATUS_NORMAL.equals(this.status);
    }

    /**
     * 判断是否为超级管理员（内置保护账号，不可删除、不可停用）。
     *
     * @return 是否超级管理员
     */
    public boolean isSuperAdmin() {
        return Constants.SUPER_ADMIN_USER_ID.equals(this.id);
    }

    /**
     * 修改密码（业务规则：必须校验原密码，且新旧密码不能相同）。
     *
     * <p>为什么把密码校验放在聚合内：密码规则是用户聚合自身的不变量，
     * 放在 Service 里会导致"绕过校验直接 setPasswordHash"的可能。
     *
     * @param hasher         密码加密策略（领域服务接口，基础设施提供 BCrypt 实现）
     * @param rawOldPassword 原密码明文
     * @param rawNewPassword 新密码明文
     * @throws IllegalArgumentException 原密码错误或新旧密码相同时抛出（由应用层转换为业务异常）
     */
    public void changePassword(PasswordHasher hasher, String rawOldPassword, String rawNewPassword) {
        if (!hasher.matches(rawOldPassword, this.passwordHash)) {
            throw new IllegalArgumentException("OLD_PASSWORD_ERROR");
        }
        if (hasher.matches(rawNewPassword, this.passwordHash)) {
            throw new IllegalArgumentException("SAME_PASSWORD");
        }
        this.passwordHash = hasher.encode(rawNewPassword);
    }

    /**
     * 管理员重置密码（无需原密码）。
     *
     * @param hasher         密码加密策略
     * @param rawNewPassword 新密码明文
     */
    public void resetPassword(PasswordHasher hasher, String rawNewPassword) {
        this.passwordHash = hasher.encode(rawNewPassword);
    }

    /**
     * 修改启停状态（业务规则：超级管理员不可停用）。
     *
     * @param newStatus 目标状态：0 正常 1 停用
     * @throws IllegalArgumentException 尝试停用超级管理员时抛出
     */
    public void changeStatus(Integer newStatus) {
        if (isSuperAdmin() && !Constants.STATUS_NORMAL.equals(newStatus)) {
            throw new IllegalArgumentException("SUPER_ADMIN_PROTECTED");
        }
        this.status = newStatus;
    }

    /**
     * 修改个人资料（只允许修改资料类字段，用户名与角色不在其中）。
     *
     * @param nickname 昵称
     * @param email    邮箱
     * @param phone    手机号
     * @param sex      性别
     * @param avatar   头像
     */
    public void updateProfile(String nickname, String email, String phone, Integer sex, String avatar) {
        this.nickname = nickname;
        this.email = email;
        this.phone = phone;
        this.sex = sex;
        this.avatar = avatar;
    }

    /**
     * 更新主题偏好（需求：跨设备同步，故持久化到用户聚合）。
     *
     * @param theme 主题：light / dark / auto
     */
    public void updateTheme(String theme) {
        this.theme = theme;
    }

    /**
     * 分配角色（整体替换，保证与提交结果一致）。
     *
     * @param roleIds 角色 ID 集合
     */
    public void assignRoles(List<Long> roleIds) {
        this.roleIds = roleIds == null ? new ArrayList<>() : new ArrayList<>(roleIds);
    }

    /**
     * 记录一次成功登录（IP 与时间）。
     *
     * @param ip   登录 IP
     * @param time 登录时间
     */
    public void recordLogin(String ip, LocalDateTime time) {
        this.loginIp = ip;
        this.loginDate = time;
    }

    /**
     * 获取角色 ID 集合（不可变视图，避免外部绕过 assignRoles 修改聚合内部状态）。
     *
     * @return 角色 ID 集合
     */
    public List<Long> getRoleIds() {
        // 空值保护：未加载关联时返回空集合，避免调用方 NPE
        return this.roleIds == null ? Collections.emptyList() : Collections.unmodifiableList(this.roleIds);
    }
}
