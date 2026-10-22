package com.example.admin.common.exception;

import cn.hutool.core.util.StrUtil;
import com.example.admin.common.result.R;
import com.example.admin.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>设计要点：
 * <ul>
 *     <li>业务异常（{@link BusinessException}）直接返回业务码与提示，不记录堆栈；</li>
 *     <li>参数校验异常自动提取字段级错误提示，避免前端只看到“参数错误”而无从定位；</li>
 *     <li>未知异常记录完整堆栈（便于排查），但响应体只返回通用提示，
 *         绝不把堆栈信息暴露给前端（安全要求：生产环境禁止泄漏内部实现细节）。</li>
 * </ul>
 *
 * <p>注意：Spring Security 过滤器链中抛出的异常（如 {@link AccessDeniedException}）
 * 不会进入本处理器，需在 {@code SecurityConfig} 中配置
 * {@code AuthenticationEntryPoint} 与 {@code AccessDeniedHandler} 返回同样的 JSON 结构。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：预期内失败，warn 级别日志即可。
     *
     * @param e      业务异常
     * @param request 当前请求
     * @return 统一响应（携带业务错误码）
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 uri={} msg={}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 认证异常：令牌失效/缺失。
     *
     * @param e       异常
     * @param request 当前请求
     * @return 401 响应
     */
    @ExceptionHandler({UnauthorizedException.class, AuthenticationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleUnauthorized(Exception e, HttpServletRequest request) {
        log.warn("认证失败 uri={} msg={}", request.getRequestURI(), e.getMessage());
        return R.fail(ResultCode.UNAUTHORIZED.getCode(),
                StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : ResultCode.UNAUTHORIZED.getMsg());
    }

    /**
     * 权限不足：已登录但无操作权限。
     *
     * @param e       异常
     * @param request 当前请求
     * @return 403 响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("权限不足 uri={} msg={}", request.getRequestURI(), e.getMessage());
        return R.fail(ResultCode.FORBIDDEN);
    }

    /**
     * {@code @RequestBody} + {@code @Validated} 校验失败。
     *
     * @param e 异常
     * @return 参数错误响应（含首个字段错误提示）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), StrUtil.isNotBlank(msg) ? msg : ResultCode.PARAM_ERROR.getMsg());
    }

    /**
     * 表单绑定校验失败（非 JSON 提交）。
     *
     * @param e 异常
     * @return 参数错误响应
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), StrUtil.isNotBlank(msg) ? msg : ResultCode.PARAM_ERROR.getMsg());
    }

    /**
     * 单参数校验失败（如 {@code @RequestParam} 上的约束）。
     *
     * @param e 异常
     * @return 参数错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), StrUtil.isNotBlank(msg) ? msg : ResultCode.PARAM_ERROR.getMsg());
    }

    /**
     * 缺少必填请求参数。
     *
     * @param e 异常
     * @return 参数错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 请求体不可解析（JSON 格式错误）。
     *
     * @param e 异常
     * @return 参数错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误，请检查 JSON 是否合法");
    }

    /**
     * 参数类型转换失败：例如 {@code /system/online/devices/abc}、请求体里 ID 传了非数字。
     *
     * <p>不加这个 handler 时，这些输入会落到下面的兜底分支变成 500 + 一条完整堆栈：
     * 前端只看到"服务器内部错误"，日志里却堆满噪音。参数错误就应该是 400。
     */
    @ExceptionHandler({NumberFormatException.class, MethodArgumentTypeMismatchException.class})
    public R<Void> handleTypeMismatch(Exception e) {
        log.warn("参数格式不正确：{}", e.getMessage());
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "参数格式不正确");
    }

    /**
     * 静态资源或接口路径不存在：返回 404，且只记 DEBUG、不打堆栈。
     *
     * <p>不加这个 handler 时，浏览器自动请求 {@code /favicon.ico}、扫描器探测
     * {@code /wp-login.php} 之类都会落进下面的兜底分支，变成 500 + 一整条堆栈：
     * 既把"资源不存在"误报成服务端故障，也把真实故障淹没在噪音里。不存在就是 404。
     *
     * @param e       异常
     * @param request 当前请求
     * @return 404 响应
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(Exception e, HttpServletRequest request) {
        log.debug("资源不存在 uri={}", request.getRequestURI());
        return R.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 兜底异常处理：记录完整堆栈，返回通用提示。
     *
     * @param e       异常
     * @param request 当前请求
     * @return 500 响应（不含堆栈）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        // 注意：日志中禁止输出密码、令牌等敏感信息；此处仅输出异常堆栈与 URI
        log.error("服务器内部错误 uri={}", request.getRequestURI(), e);
        return R.fail(ResultCode.ERROR);
    }

    /**
     * 格式化字段错误为 “字段名: 错误提示”。
     *
     * @param fieldError 字段错误
     * @return 格式化后的提示
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
