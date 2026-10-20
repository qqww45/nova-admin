package com.example.admin.common.exception;

import com.example.admin.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于 Service 层主动抛出的可预期异常（如“数据已存在”“账号被锁定”），
 * 由 {@code GlobalExceptionHandler} 统一转换为 {@code R} 响应，
 * 不打堆栈、不记录 ERROR 日志（避免噪音）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final int code;

    /**
     * 使用默认业务错误码（1000）构造异常。
     *
     * @param message 异常提示
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }

    /**
     * 使用指定错误码枚举构造异常。
     *
     * @param resultCode 错误码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    /**
     * 使用错误码枚举 + 自定义提示构造异常（提示需更具体时使用）。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义提示信息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 使用自定义错误码构造异常。
     *
     * @param code    错误码
     * @param message 异常提示
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
