package com.example.admin.domain.model.role;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

/**
 * 数据权限过滤条件（值对象）：由 {@code DataScopeAspect} 解析后放入线程上下文，
 * 供基础设施层的查询实现读取并拼接 SQL 条件。
 *
 * <p>三种形态（互斥）：
 * <ul>
 *     <li>{@code unrestricted=true}：不限制（全部数据 / 超级管理员）；</li>
 *     <li>{@code deptIds != null}：按部门集合过滤；</li>
 *     <li>{@code selfUserId != null}：按用户本人过滤。</li>
 * </ul>
 *
 * <p>为什么用"集合"而不是 SQL 片段拼接：
 * 避免把 SQL 串拼进上下文（那是注入风险与耦合来源），
 * 查询实现拿到的是纯数据，由 MyBatis-Plus 参数化生成 {@code IN (...)}。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
public class DataScopeFilter {

    /** 是否不限制（true 时忽略 deptIds 与 selfUserId） */
    private final boolean unrestricted;

    /** 允许查看的部门 ID 集合（空集合表示"查不到任何数据"，注意与 unrestricted 区分） */
    private final Set<Long> deptIds;

    /** 仅本人数据时，限制的用户 ID */
    private final Long selfUserId;

    /**
     * 构造"不限制"的过滤器。
     */
    public static DataScopeFilter unrestricted() {
        return DataScopeFilter.builder().unrestricted(true).deptIds(Collections.emptySet()).build();
    }

    /**
     * 构造"按部门过滤"的过滤器。
     */
    public static DataScopeFilter byDept(Set<Long> deptIds) {
        return DataScopeFilter.builder()
                .unrestricted(false)
                .deptIds(deptIds == null ? Collections.emptySet() : deptIds)
                .build();
    }

    /**
     * 构造"仅本人"的过滤器。
     */
    public static DataScopeFilter bySelf(Long userId) {
        return DataScopeFilter.builder().unrestricted(false).selfUserId(userId).build();
    }
}
