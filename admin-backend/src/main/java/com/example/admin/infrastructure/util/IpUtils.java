package com.example.admin.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * IP 工具类。
 *
 * <p>注意：{@code X-Forwarded-For} 等请求头可被客户端伪造，
 * 因此仅在存在可信反向代理（Nginx / 网关）时才应信任；
 * 直接暴露在公网时，应以 {@code request.getRemoteAddr()} 为准。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IpUtils {

    /** 常见代理头，按优先级排列 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    /** 内网 IP 段正则（用于判断是否来自内网） */
    private static final Pattern INTERNAL_IP_PATTERN = Pattern.compile(
            "^(127\\.0\\.0\\.1)|" +
                    "^(10\\.)|" +
                    "^(172\\.(1[6-9]|2\\d|3[01])\\.)|" +
                    "^(192\\.168\\.)|" +
                    "^(::1)|" +
                    "^(0:0:0:0:0:0:0:1)");

    /**
     * 获取客户端真实 IP。
     *
     * @param request 请求对象
     * @return IP 地址，获取失败返回空字符串
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String remoteAddr = request.getRemoteAddr();
        // 仅当 TCP 对端是"可信代理"（本机或内网反向代理）时才解析 X-Forwarded-For。
        // 否则一律使用 remoteAddr —— 该头是客户端可任意伪造的，无条件信任它意味着：
        // IP 限流（换一个头就绕过）、票据绑定的网段、登录审计 IP 三项一起失效。
        if (isTrustedProxy(remoteAddr)) {
            for (String header : IP_HEADERS) {
                String ip = request.getHeader(header);
                if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                    // X-Forwarded-For 可能为 “client, proxy1, proxy2”，第一个为客户端 IP
                    int index = ip.indexOf(',');
                    if (index > 0) {
                        ip = ip.substring(0, index);
                    }
                    return ip.trim();
                }
            }
        }
        return remoteAddr == null ? "" : remoteAddr;
    }

    /**
     * 判断来源是否为可信代理：本机回环与私有网段（内网反向代理的常见来源）。
     *
     * <p>为什么只认内网：公网来源声称"我转发了某 IP"是不可验证的断言，
     * 只有部署方自己控制的代理才值得信任。生产若在内网之外有独立的反代，
     * 应把它的地址加入白名单（可通过配置扩展）。
     */
    private static boolean isTrustedProxy(String addr) {
        if (addr == null || addr.isBlank()) {
            return false;
        }
        String a = addr.trim();
        if (a.equals("127.0.0.1") || a.equals("::1") || a.equals("0:0:0:0:0:0:0:1") || a.equals("localhost")) {
            return true;
        }
        if (a.startsWith("10.") || a.startsWith("192.168.")) {
            return true;
        }
        if (a.startsWith("172.")) {
            String[] parts = a.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ex) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 判断 IP 是否为内网地址。
     *
     * @param ip IP 地址
     * @return 是否内网
     */
    public static boolean isInternalIp(String ip) {
        return ip != null && INTERNAL_IP_PATTERN.matcher(ip).find();
    }

    /**
     * 获取本机 IP（用于日志/健康检查展示）。
     *
     * @return 本机 IP，获取失败返回 127.0.0.1
     */
    public static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /**
     * 解析 User-Agent 中的浏览器类型（轻量解析，不引入第三方 UA 库）。
     *
     * @param request 请求对象
     * @return 浏览器名称
     */
    public static String getBrowser(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        if (ua == null || ua.isBlank()) {
            return "Unknown";
        }
        String lower = ua.toLowerCase();
        if (lower.contains("edg")) {
            return "Edge";
        }
        if (lower.contains("chrome")) {
            return "Chrome";
        }
        if (lower.contains("firefox")) {
            return "Firefox";
        }
        if (lower.contains("safari")) {
            return "Safari";
        }
        return "Other";
    }

    /**
     * 解析 User-Agent 中的操作系统（轻量解析）。
     *
     * @param request 请求对象
     * @return 操作系统名称
     */
    /**
     * 提取 IP 所属网段（IPv4 取 /24，IPv6 取前 4 段）。
     *
     * <p>为什么按网段而不是完整 IP：
     * 家用宽带与办公网的 IP 会频繁变化，用完整 IP 做绑定会让用户换个 Wi-Fi 就被判为陌生设备；
     * 网段既能识别"来源网络发生了实质性变化"，又不会因 DHCP 重新分配而误判。
     *
     * @param ip IP 地址
     * @return 网段标识
     */
    public static String getIpSegment(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String value = ip.trim();
        if (value.contains(":")) {
            String[] parts = value.split(":");
            int length = Math.min(4, parts.length);
            return String.join(":", java.util.Arrays.copyOf(parts, length)) + "::/64";
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return value;
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
    }

    public static String getOs(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        if (ua == null || ua.isBlank()) {
            return "Unknown";
        }
        String lower = ua.toLowerCase();
        if (lower.contains("windows")) {
            return "Windows";
        }
        if (lower.contains("mac")) {
            return "macOS";
        }
        if (lower.contains("android")) {
            return "Android";
        }
        if (lower.contains("iphone") || lower.contains("ipad")) {
            return "iOS";
        }
        if (lower.contains("linux")) {
            return "Linux";
        }
        return "Other";
    }
}
