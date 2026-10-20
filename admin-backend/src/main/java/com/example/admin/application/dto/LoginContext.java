package com.example.admin.application.dto;

/**
 * 登录请求的客户端上下文（IP、UA、浏览器、操作系统、网段、指纹）。
 *
 * <p>存在理由：应用层原先直接接收 {@code HttpServletRequest}，再从里面掏 IP、UA 与请求头。
 * 这看起来只是顺手，实际把应用层钉死在了 Servlet 容器上 —— 一旦有非 HTTP 的调用方
 * （定时任务、消息消费者、单元测试、未来的 gRPC 接口），就必须凭空造一个 request 对象，
 * 或者把风控逻辑复制一份出去。应用层关心的是"这次登录来自哪里"，
 * 而不是"这次登录是通过哪个 Servlet 请求进来的"，这两者应当分开。
 *
 * <p>因此这里用一个不可变值对象承载应用层真正需要的字段，由接口层负责从请求里提取并填充。
 * 应用层只依赖这个类，不再感知 Web 框架。
 *
 * @param ip          客户端 IP
 * @param userAgent   原始 User-Agent（风控与设备推导都要用）
 * @param browser     浏览器名称（用于登录日志展示）
 * @param os          操作系统名称（用于登录日志展示）
 * @param fingerprint 设备指纹请求头（可能为 null）
 * @param ipSegment   来源网段（票据绑定，防令牌换网段使用）
 * @author admin-system
 * @since 2026-01-01
 */
public record LoginContext(
        String ip,
        String userAgent,
        String browser,
        String os,
        String fingerprint,
        String ipSegment
) {

    /**
     * 无上下文场景（内部调用、定时任务）使用：各项均为空或未知，风控会按"信息缺失"处理。
     *
     * @return 空上下文
     */
    public static LoginContext empty() {
        return new LoginContext("", "", "Unknown", "Unknown", null, "");
    }
}
