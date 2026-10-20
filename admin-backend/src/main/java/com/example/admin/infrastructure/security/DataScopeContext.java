package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.role.DataScopeFilter;

/**
 * 数据权限线程上下文。
 *
 * <p>为什么用 ThreadLocal 而不是把过滤条件当方法参数一路传下去：
 * 查询方法（{@code search(UserQuery)}）的签名已经很臃肿，再为每个业务表加一个"可见范围"参数
 * 会污染所有调用点；ThreadLocal 让拦截层把"当前请求能看到什么"准备好，查询层按需读取即可。
 *
 * <p>风险与对策：
 * <ul>
 *     <li><b>风险</b>：线程池复用导致上下文串到别的请求；</li>
 *     <li><b>对策</b>：切面在 {@code finally} 中必定 {@link #clear()}，
 *         且每次 {@code set} 前先清理，不依赖调用方自觉。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class DataScopeContext {

    private static final ThreadLocal<DataScopeFilter> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /**
     * 设置当前线程的数据权限过滤条件。
     */
    public static void set(DataScopeFilter filter) {
        HOLDER.set(filter);
    }

    /**
     * 读取当前线程的过滤条件（未设置返回 null，表示"不启用数据权限"）。
     */
    public static DataScopeFilter get() {
        return HOLDER.get();
    }

    /**
     * 清理：必须在请求结束时调用，避免线程池复用导致越权。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
