package com.example.admin.domain.model.auth;

import java.util.Objects;

/**
 * 设备指纹（值对象）。
 *
 * <p>为什么不是直接用 userAgent 当设备标识：
 * <ul>
 *     <li>同一台机器上 Chrome / Edge 的 UA 不同，会误判为陌生设备；</li>
 *     <li>UA 可被伪造，单独使用不足以作为信任依据；</li>
 *     <li>IP 会漂移（家庭宽带重拨、办公网 DHCP），需要用"IP 段"而不是完整 IP。</li>
 * </ul>
 * 因此采用 <b>UA + IP 段 + 浏览器指纹哈希</b> 三要素合成，兼顾稳定性与不可伪造性。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class DeviceFingerprint {

    /** 浏览器 User-Agent */
    private final String userAgent;

    /** 客户端 IP */
    private final String ip;

    /** 前端生成的浏览器指纹（canvas / 字体 / 屏幕等特征哈希） */
    private final String fingerprintHash;

    public DeviceFingerprint(String userAgent, String ip, String fingerprintHash) {
        this.userAgent = userAgent == null ? "" : userAgent;
        this.ip = ip == null ? "" : ip;
        this.fingerprintHash = fingerprintHash == null ? "" : fingerprintHash;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIp() {
        return ip;
    }

    public String getFingerprintHash() {
        return fingerprintHash;
    }

    /**
     * 取 IP 段（IPv4 取前 3 段即 /24，IPv6 取前 4 段）。
     *
     * <p>原因：完整 IP 太敏感且易变，段级粒度既能识别"异地登录"，
     * 又不会因为用户换 Wi-Fi 就判定为陌生设备（这是"低打扰"的关键）。
     *
     * @return IP 段
     */
    public String getIpSegment() {
        if (ip.isBlank()) {
            return "unknown";
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(4, parts.length); i++) {
                sb.append(parts[i]).append(':');
            }
            return sb.toString();
        }
        String[] parts = ip.split("\\.");
        if (parts.length < 3) {
            return ip;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeviceFingerprint other)) {
            return false;
        }
        return Objects.equals(userAgent, other.userAgent)
                && Objects.equals(ip, other.ip)
                && Objects.equals(fingerprintHash, other.fingerprintHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userAgent, ip, fingerprintHash);
    }
}
