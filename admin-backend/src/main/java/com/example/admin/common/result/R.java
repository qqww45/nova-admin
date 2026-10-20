package com.example.admin.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 统一响应体 {@code R<T>}。
 *
 * <p>全站 RESTful 接口统一返回 {@code { code, msg, data }} 结构，前端 axios 拦截器
 * 只需按 code 判断成功与否，避免各接口返回结构不一致导致的适配成本。
 *
 * <p>约定：
 * <ul>
 *     <li>code = 200 表示成功；</li>
 *     <li>code = 401 未认证（跳转登录）；403 无权限；500 服务器错误；</li>
 *     <li>code >= 1000 为自定义业务错误码，见 {@link ResultCode}。</li>
 * </ul>
 *
 * @param <T> 响应数据类型
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Accessors(chain = true)
@Schema(name = "R", description = "统一响应体")
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "状态码，200 成功")
    private int code;

    @Schema(description = "提示信息")
    private String msg;

    @Schema(description = "响应数据")
    private T data;

    /**
     * 构造成功响应（无数据）。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok() {
        return new R<T>().setCode(ResultCode.SUCCESS.getCode()).setMsg(ResultCode.SUCCESS.getMsg());
    }

    /**
     * 构造成功响应（带数据）。
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok(T data) {
        // 显式指定类型参数，避免泛型推断退化为 R<Object>
        return R.<T>ok().setData(data);
    }

    /**
     * 构造成功响应（自定义提示 + 数据）。
     *
     * @param msg  提示信息
     * @param data 数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok(String msg, T data) {
        return new R<T>().setCode(ResultCode.SUCCESS.getCode()).setMsg(msg).setData(data);
    }

    /**
     * 构造失败响应（默认业务错误码 500）。
     *
     * @param msg  错误提示
     * @param <T>  数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(String msg) {
        return new R<T>().setCode(ResultCode.ERROR.getCode()).setMsg(msg);
    }

    /**
     * 构造失败响应（指定错误码）。
     *
     * @param code 错误码
     * @param msg  错误提示
     * @param <T>  数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(int code, String msg) {
        return new R<T>().setCode(code).setMsg(msg);
    }

    /**
     * 构造失败响应（使用 {@link ResultCode} 枚举）。
     *
     * @param resultCode 错误码枚举
     * @param <T>        数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<T>().setCode(resultCode.getCode()).setMsg(resultCode.getMsg());
    }

    /**
     * 构造失败响应（错误码枚举 + 自定义提示，用于需要更具体提示的场景）。
     *
     * @param resultCode 错误码枚举
     * @param msg        自定义提示
     * @param <T>        数据类型
     * @return 失败响应
     */
    /**
     * 失败响应（携带数据）。
     *
     * <p>用途：某些"失败"其实是流程的中间态，需要把后续步骤所需的凭证回传给前端。
     * 典型场景：登录需要人机验证时，必须把一次性票据带回给前端，
     * 否则前端无法知道该拿哪张票据去完成验证。
     *
     * @param resultCode 结果码
     * @param data       附带数据
     * @return 响应对象
     */
    public static <T> R<T> fail(ResultCode resultCode, T data) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMsg(resultCode.getMsg());
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode, String msg) {
        return new R<T>().setCode(resultCode.getCode()).setMsg(msg);
    }
}
