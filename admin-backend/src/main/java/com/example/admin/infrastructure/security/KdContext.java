package com.example.admin.infrastructure.security;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前采集器身份。
 *
 * <p>采集接口的归属人（owner_user_id）与账套一律从这里取，
 * <b>绝不接受请求体里的 ownerUserId</b>——否则采集器可以伪造别人的数据归属。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public final class KdContext {

    private KdContext() {
        // 工具类不允许实例化
    }

    /**
     * 取当前采集器身份。
     *
     * @return 身份
     * @throws BusinessException 未认证（正常已被安全过滤器拦截，走到这里是代码缺陷）
     */
    public static KdPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof KdPrincipal principal) {
            return principal;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED, "采集器未认证，请重新绑定");
    }
}
