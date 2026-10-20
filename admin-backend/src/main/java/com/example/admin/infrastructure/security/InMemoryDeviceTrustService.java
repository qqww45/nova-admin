package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.auth.DeviceFingerprint;
import com.example.admin.domain.model.auth.DeviceTrust;
import com.example.admin.domain.service.DeviceTrustPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可信设备策略的内存实现。
 *
 * <p>为什么先用内存：本项目默认单机部署，不强制依赖 Redis。
 * 接口 {@link DeviceTrustPolicy} 已按 Redis 语义设计，切换到 Redis 时
 * 只需新增一个 {@code RedisDeviceTrustService} 并把本类换掉即可，业务代码零改动：
 * <pre>
 *     SET device:trust:{userId}:{deviceId} {信任时间} EX {ttl}
 * </pre>
 *
 * <p>内存实现的边界（务必知晓）：
 * <ul>
 *     <li>应用重启后信任记录丢失 → 用户需要重新过一次验证码（安全上可接受，属于"降级为严格"）；</li>
 *     <li>多实例部署时各实例不共享 → 会重复要求验证码，此时应换 Redis 实现。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryDeviceTrustService implements DeviceTrustPolicy {

    /** 信任记录：建立时间 + 过期时间 */
    private record TrustRecord(Instant trustAt, Instant expireAt) {
    }

    /** key = "{userId}:{deviceId}" */
    private final Map<String, TrustRecord> trustStore = new ConcurrentHashMap<>();

    /**
     * 生成设备标识：对"UA + IP 段 + 浏览器指纹"做 SHA-256 摘要。
     *
     * <p>用摘要而不是原始串的原因：设备信息属于敏感数据，
     * 存储明文 UA/IP 既占空间又有合规风险，摘要后只用于比对。
     *
     * @param fingerprint 设备指纹
     * @return 32 位十六进制设备标识
     */
    @Override
    public String resolveDeviceId(DeviceFingerprint fingerprint) {
        String raw = fingerprint.getUserAgent() + '|' + fingerprint.getIpSegment() + '|' + fingerprint.getFingerprintHash();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (int i = 0; i < 16; i++) {
                sb.append(Character.forDigit((hash[i] >> 4) & 0xf, 16))
                        .append(Character.forDigit(hash[i] & 0xf, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现的算法，理论上不可达；退化方案仍保证可用性
            log.error("SHA-256 不可用，设备标识退化为哈希码", e);
            return String.valueOf(raw.hashCode());
        }
    }

    @Override
    public boolean isTrusted(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return false;
        }
        String key = key(userId, deviceId);
        TrustRecord record = trustStore.get(key);
        if (record == null) {
            return false;
        }
        if (record.expireAt().isBefore(Instant.now())) {
            // 已过期：惰性删除，避免脏数据常驻
            trustStore.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public void trust(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        trustStore.put(key(userId, deviceId), new TrustRecord(now, now.plus(DEFAULT_TRUST_TTL)));
        log.info("设备已标记为可信 userId={} deviceId={} ttlDays={}", userId, deviceId, DEFAULT_TRUST_TTL.toDays());
    }

    @Override
    public void revoke(Long userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return;
        }
        trustStore.remove(key(userId, deviceId));
    }

    @Override
    public int revokeAll(Long userId) {
        if (userId == null) {
            return 0;
        }
        String prefix = userId + ":";
        List<String> keys = new ArrayList<>();
        for (String key : trustStore.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        keys.forEach(trustStore::remove);
        if (!keys.isEmpty()) {
            log.info("已撤销用户 {} 的 {} 个可信设备", userId, keys.size());
        }
        return keys.size();
    }

    @Override
    public List<DeviceTrust> listTrusted(Long userId) {
        if (userId == null) {
            return List.of();
        }
        String prefix = userId + ":";
        List<DeviceTrust> result = new ArrayList<>();
        Instant now = Instant.now();
        for (Map.Entry<String, TrustRecord> entry : trustStore.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            TrustRecord record = entry.getValue();
            if (record.expireAt().isBefore(now)) {
                trustStore.remove(entry.getKey());
                continue;
            }
            // key 形如 "{userId}:{deviceId}"，去掉前缀即为设备标识
            String deviceId = entry.getKey().substring(prefix.length());
            result.add(new DeviceTrust(deviceId,
                    LocalDateTime.ofInstant(record.trustAt(), ZoneId.systemDefault()),
                    LocalDateTime.ofInstant(record.expireAt(), ZoneId.systemDefault())));
        }
        return result.stream()
                .sorted((a, b) -> b.getTrustTime().compareTo(a.getTrustTime()))
                .toList();
    }

    /**
     * 清理过期的信任记录（每小时执行一次）。
     *
     * <p>TTL 由存储自身管理是最佳实践（Redis EXPIRE）；内存实现只能自行回收，
     * 这里惰性删除 + 定时扫描双保险，避免长期运行后内存膨胀。
     */
    @Scheduled(fixedDelay = 3600_000)
    public void cleanExpired() {
        Instant now = Instant.now();
        int removed = 0;
        for (Map.Entry<String, TrustRecord> entry : trustStore.entrySet()) {
            if (entry.getValue().expireAt().isBefore(now)) {
                trustStore.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("清理过期可信设备记录 {} 条", removed);
        }
    }

    /**
     * 构造存储键（与 Redis Key 设计保持一致，便于平移）。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return 存储键
     */
    private String key(Long userId, String deviceId) {
        return userId + ":" + deviceId;
    }

    /**
     * 信任有效期（与接口默认一致，便于测试与后续配置化）。
     *
     * @return 有效期
     */
    public Duration trustTtl() {
        return DEFAULT_TRUST_TTL;
    }
}
