package com.example.admin.infrastructure.security;

import com.example.admin.domain.service.DataScopePolicy;
import com.example.admin.infrastructure.security.annotation.DataScopeAnno;
import com.example.admin.infrastructure.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 数据权限切面：解析"当前用户能看到哪些行"，放入 {@link DataScopeContext}，方法结束后必定清理。
 *
 * <p>为什么用切面而不是在每个 Service 里手写过滤：
 * 数据权限是横切关注点，散落在各个业务方法里必然会出现"某个查询忘了过滤"的漏洞；
 * 集中在切面里，业务只要加一个注解就自动生效。
 *
 * <p>多角色取"最宽松"：同时拥有"本部门"和"全部数据"两个角色时按"全部数据"处理，
 * 避免出现"权限越加越小"的反直觉行为（用户会以为加角色是提权）。
 *
 * <p>未登录时按最严格（仅本人）处理：宁可查不到，也不要因为"上下文缺失"变成全量可见。
 *
 * <p><b>当前实现范围</b>：{@link DataScope#ALL}、{@link DataScope#DEPT}、{@link DataScope#SELF}。
 * {@code DEPT_AND_CHILD} 与 {@code CUSTOM} 已在枚举中定义，但暂按 {@code DEPT} 降级处理
 * （见 {@link #resolveFilter}），接入时需补：子部门递归查询、{@code sys_role_dept} 表。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    /**
     * 可见范围的解析规则放在领域服务里：列表查询用它的过滤条件，
     * 单条操作用它的判定方法，避免"列表管得住、单条管不住"。
     */
    private final DataScopePolicy dataScopePolicy;

    /**
     * 拦截带 {@link DataScopeAnno} 的方法。
     */
    @Around("@annotation(dataScopeAnno)")
    public Object apply(ProceedingJoinPoint joinPoint, DataScopeAnno dataScopeAnno) throws Throwable {
        try {
            DataScopeContext.set(dataScopePolicy.filterFor(SecurityUtils.getLoginUser()));
            return joinPoint.proceed();
        } finally {
            // 必须清理：线程由容器复用，残留上下文会让下一个请求"继承"上一个用户的可见范围
            DataScopeContext.clear();
        }
    }

}
