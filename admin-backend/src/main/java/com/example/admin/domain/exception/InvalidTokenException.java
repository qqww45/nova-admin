package com.example.admin.domain.exception;

/**
 * 令牌无效（签名错误 / 过期 / 格式非法 / 类型不符）。
 *
 * <p>为什么不用 JJWT 自己的 {@code JwtException}：那样等于把 JWT 库的类型泄漏进领域层，
 * 换实现（比如改成不透明令牌或引入新的签名库）时，调用方要跟着改。
 * 领域层只认这个异常，具体是什么库抛的由基础设施适配器翻译。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class InvalidTokenException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 说明
     */
    public InvalidTokenException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 说明
     * @param cause   原始异常
     */
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
