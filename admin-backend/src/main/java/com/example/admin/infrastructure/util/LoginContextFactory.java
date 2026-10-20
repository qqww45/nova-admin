package com.example.admin.infrastructure.util;

import com.example.admin.application.dto.LoginContext;
import com.example.admin.common.constant.Constants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 从 {@link HttpServletRequest} 提取客户端上下文。
 *
 * <p>这是"Web 细节"与"应用逻辑"的分界线：读取请求头、解析 UA、判定可信代理拿真实 IP
 * 都属于基础设施职责，所以放在这里；应用层只见 {@link LoginContext}，不再触碰 Servlet API。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoginContextFactory {

    /**
     * 提取客户端上下文。
     *
     * @param request 当前请求（为 null 时返回空上下文）
     * @return 客户端上下文
     */
    public static LoginContext from(HttpServletRequest request) {
        if (request == null) {
            return LoginContext.empty();
        }
        String ip = IpUtils.getIpAddress(request);
        return new LoginContext(
                ip,
                request.getHeader("User-Agent"),
                IpUtils.getBrowser(request),
                IpUtils.getOs(request),
                request.getHeader(Constants.DEVICE_FINGERPRINT_HEADER),
                IpUtils.getIpSegment(ip)
        );
    }
}
