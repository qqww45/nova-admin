package com.example.admin.common.exception;

import com.example.admin.common.result.ResultCode;

/**
 * 认证异常（401）。
 *
 * <p>用于令牌缺失、令牌过期、令牌已被加入黑名单等场景。
 * 前端 axios 拦截器收到 401 后会清除本地登录态并跳转登录页。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class UnauthorizedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码，固定为 401 */
    private final int code = ResultCode.UNAUTHORIZED.getCode();

    /**
     * 使用默认提示构造。
     */
    public UnauthorizedException() {
        super(ResultCode.UNAUTHORIZED.getMsg());
    }

    /**
     * 使用自定义提示构造。
     *
     * @param message 提示信息（注意：禁止包含令牌等敏感信息）
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * 获取错误码。
     *
     * @return 401
     */
    public int getCode() {
        return code;
    }
}
