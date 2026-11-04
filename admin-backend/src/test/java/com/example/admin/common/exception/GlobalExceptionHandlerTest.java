package com.example.admin.common.exception;

import com.example.admin.common.result.R;
import com.example.admin.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常响应文案测试。
 *
 * <p>回归背景：金蝶采集器 1.2.1 把本地 ISO 水位（{@code 2026-11-03T12:00:00.000Z}）
 * 写进 finish 请求后，Jackson 抛 {@code HttpMessageNotReadableException}，
 * 页面上只看到「请求体格式错误，请检查 JSON 是否合法」，运维无法区分
 * 「真 JSON 坏了 / 日期格式 / 请求被截断 / 数字溢出」。
 *
 * @author admin-system
 * @since 2026-11-04
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("日期格式不匹配应回传具体原因，而不是笼统的 JSON 不合法")
    void dateFormatMismatchShouldSurfaceReason() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error: 日期时间格式不正确：2026-11-03T12:00:00.000Z，应为 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd");

        R<Void> result = handler.handleNotReadable(e);

        assertEquals(ResultCode.PARAM_ERROR.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("日期时间格式不正确"), "应露出日期原因，实际：" + result.getMsg());
        assertFalse(result.getMsg().contains("JSON 是否合法"), "不应再掩盖成 JSON 语法错误");
    }

    @Test
    @DisplayName("JSON 读到一半结束应提示请求体不完整/被截断")
    void unexpectedEofShouldSayIncompleteBody() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected end-of-input: expected close marker for Object");

        R<Void> result = handler.handleNotReadable(e);

        assertEquals(ResultCode.PARAM_ERROR.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("不完整") || result.getMsg().contains("截断"),
                "截断应有明确提示，实际：" + result.getMsg());
    }

    @Test
    @DisplayName("无法归类的解析失败仍回友好兜底文案")
    void genericGarbageStillGetsFriendlyFallback() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error: Unrecognized token 'foo'");

        R<Void> result = handler.handleNotReadable(e);

        assertEquals(ResultCode.PARAM_ERROR.getCode(), result.getCode());
        assertEquals("请求体格式错误，请检查 JSON 是否合法", result.getMsg());
    }

    @Test
    @DisplayName("唯一键冲突不应落成 500 服务器内部错误")
    void uniqueKeyShouldNotBeInternalError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/kd/sync/outbound-line");
        DataIntegrityViolationException e = new DataIntegrityViolationException("Duplicate entry");

        R<Void> result = handler.handleDataIntegrity(e, request);

        assertEquals(ResultCode.DATA_EXIST.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("冲突") || result.getMsg().contains("并发"),
                "应提示并发/冲突以便重试，实际：" + result.getMsg());
        assertFalse(result.getMsg().contains("服务器内部错误"));
    }
}
