package com.example.admin.infrastructure.cache.redis;

import com.example.admin.domain.model.auth.DeviceFingerprint;
import com.example.admin.domain.model.auth.DeviceTrust;
import com.example.admin.domain.service.DeviceTrustPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
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

/**
 * 可信设备策略的 Redis 实现。
 *
 * <p>Key 设计：{@code admin:device:trust:{userId}:{deviceId}}，TTL 30 天。
 * 列出某用户全部可信设备用 SCAN（多用户量大时 KEYS 会阻塞主线程）。
 *
 * <p>{@link #resolveDeviceId} 是<b>纯计算</b>（SHA-256），与存储无关，
 * 内存与 Redis 实现的算法必须完全一致 —— 设备标识错一位就会让所有已信任设备失效。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "redis")
public class RedisDeviceTrustService implements DeviceTrustPolicy {

    private static final String KEY_PREFIX = "admin:device:trust:";

    private final StringRedisTemplate redis;

    private final ObjectMapper objectMapper;

    /**
     * 生成设备标识：对"UA + IP 段 + 浏览器指纹"做 SHA-256 摘要。
     *
     * <p>用摘要而不是原始串的原因：设备信息属于敏感数据，
     * 存储明文 UA/IP 既占空间又有合规风险，摘要后只用于比对。
     *
     * <p>实现必须与 {@code InMemoryDeviceTrustService.resolveDeviceId} 字节级一致，
     * 否则换 Redis 时所有已信任设备会被识别为陌生 → 用户被迫重新验证。
     */
    @Override
    public String resolveDeviceId(DeviceFingerprint fingerprint) {
        String raw = fingerprint.getUserAgent() + '|'
                + fingerprint.getIpSegment() + '|'
                + fingerprint.getFingerprintHash();
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
            log.error("SHA-256 不可用，设备标识退化为哈希码", e);
            return String.valueOf(raw.hashCode());
        }
    }

    @Override
    public boolean isTrusted(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return false;
        }
        Boolean exists = redis.hasKey(key(userId, deviceId));
        // TTL 由 Redis 自动管理：过期后 Key 自动删除，hasKey 返回 false
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void trust(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        LocalDateTime trustTime = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        LocalDateTime expireTime = LocalDateTime.ofInstant(now.plus(DEFAULT_TRUST_TTL), ZoneId.systemDefault());
        redis.opsForValue().set(key(userId, deviceId),
                RedisJsonSupport.write(objectMapper, new DeviceTrust(deviceId, trustTime, expireTime)),
                DEFAULT_TRUST_TTL);
        log.info("设备已标记为可信 userId={} deviceId={} ttlDays={}", userId, deviceId, DEFAULT_TRUST_TTL.toDays());
    }

    @Override
    public void revoke(Long userId, String deviceId) {
        if (userId != null && deviceId != null) {
            redis.delete(key(userId, deviceId));
        }
    }

    @Override
    public int revokeAll(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<String> keys = scanKeys(KEY_PREFIX + userId + ":*");
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redis.delete(keys);
        int count = deleted == null ? 0 : deleted.intValue();
        if (count > 0) {
            log.info("已撤销用户 {} 的 {} 个可信设备", userId, count);
        }
        return count;
    }

    @Override
    public List<DeviceTrust> listTrusted(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<String> keys = scanKeys(KEY_PREFIX + userId + ":*");
        if (keys.isEmpty()) {
            return List.of();
        }
        String prefix = KEY_PREFIX + userId + ":";
        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null) {
            return List.of();
        }
        List<DeviceTrust> result = new ArrayList<>();
        for (int i = 0; i < keys.size() && i < values.size(); i++) {
            String json = values.get(i);
            if (json == null) {
                continue;
            }
            DeviceTrust trust = RedisJsonSupport.read(objectMapper, json, DeviceTrust.class);
            if (trust != null) {
                result.add(trust);
            }
        }
        return result.stream()
                .sorted((a, b) -> b.getTrustTime().compareTo(a.getTrustTime()))
                .toList();
    }

    /**
     * 用 SCAN 扫描匹配的 Key。
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    private static String key(Long userId, String deviceId) {
        return KEY_PREFIX + userId + ":" + deviceId;
    }
}
