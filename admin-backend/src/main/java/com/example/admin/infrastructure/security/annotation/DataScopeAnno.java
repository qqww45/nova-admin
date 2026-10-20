package com.example.admin.infrastructure.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记该方法需要应用数据权限。
 *
 * <p>用法：标注在<b>应用服务或控制器的查询方法</b>上。
 * 切面（{@code DataScopeAspect}）在方法执行前解析当前用户的可见范围并放入 {@code DataScopeContext}，
 * 执行后必定清理；查询实现（如 {@code UserRepositoryImpl.search}）读取上下文拼 SQL 条件。
 *
 * <p>为什么注解名带 Anno 后缀：与领域枚举 {@code com.example.admin.domain.model.role.DataScope} 重名，
 * 同处一个切面文件时会产生引用歧义，显式区分更省心。
 *
 * <p>接入新业务表的步骤：
 * <ol>
 *     <li>在查询方法上加本注解；</li>
 *     <li>在对应的 Repository 实现里读取 {@code DataScopeContext.get()} 并拼条件；</li>
 *     <li>确保表有归属字段（{@code dept_id} 或 {@code create_by}）。</li>
 * </ol>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScopeAnno {

    /**
     * 归属字段名：dept_id（按部门）或 create_by（按创建人）。
     * 仅供阅读与后续扩展使用（当前实现固定按 dept_id）。
     */
    String ownerField() default "dept_id";
}
