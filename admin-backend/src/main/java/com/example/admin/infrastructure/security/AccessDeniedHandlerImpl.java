package com.example.admin.infrastructure.security;

import com.example.admin.common.result.R;
import com.example.admin.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 无权限（403）处理器。
 *
 * <p>用于 {@code @PreAuthorize} 校验失败、或过滤器链中权限不足的场景，
 * 统一返回 {@code {code:403,msg:"没有权限访问该资源"}}，前端提示“权限不足”。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * 处理权限不足的请求。
     *
     * @param request               请求
     * @param response              响应
     * @param accessDeniedException 权限异常
     * @throws IOException 写入响应失败时抛出
     */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("权限不足 uri={}", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        R<Void> result = R.fail(ResultCode.FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
