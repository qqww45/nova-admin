package com.example.admin.domain.model.user;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户查询条件（领域查询对象）。
 *
 * <p>分层说明：查询条件属于领域层的"规约（Specification）"简化实现，
 * 由 Repository 实现翻译为具体的查询语言（此处为 MyBatis-Plus LambdaQueryWrapper），
 * 从而保证 Application 层不出现任何 ORM 细节。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：匹配用户名或昵称 */
    private String keyword;

    /** 手机号 */
    private String phone;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 部门 ID */
    private Long deptId;

    /** 创建时间起 */
    private LocalDateTime beginTime;

    /** 创建时间止 */
    private LocalDateTime endTime;
}
