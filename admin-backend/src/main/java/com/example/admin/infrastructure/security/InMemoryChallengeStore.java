package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.auth.ChallengeTicket;
import com.example.admin.domain.service.ChallengeStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证票据的内存实现。
 *
 * <p>抗重放的关键在 {@link #consume}：
 * 先用 {@code remove} 把票据从存储中"取走"（原子操作），再校验上下文与有效期。
 * 这样无论多少并发请求携带同一票据，只有一个能拿到对象，其余全部落空 ——
 * 天然的"一次一销"，不依赖任何额外的锁。
 *
 * <p>内存实现的边界：应用重启票据即失效（表现为"重新验证一次"）；
 * 多实例部署需换 Redis 实现，用 GETDEL 或 Lua 保证"读取 + 删除"的原子性（见接口注释）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "admin.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryChallengeStore implements ChallengeStore {

    private final Map<String, ChallengeTicket> store = new ConcurrentHashMap<>();

    @Override
    public void save(ChallengeTicket ticket) {
        if (ticket == null) {
            return;
        }
        store.put(ticket.getTicketId(), ticket);
    }

    @Override
    public ChallengeTicket get(String ticketId) {
        if (ticketId == null) {
            return null;
        }
        ChallengeTicket ticket = store.get(ticketId);
        if (ticket == null) {
            return null;
        }
        if (ticket.isExpired()) {
            store.remove(ticketId);
            return null;
        }
        return ticket;
    }

    @Override
    public ChallengeTicket consume(String ticketId, String username, String deviceId, String ipSegment) {
        if (ticketId == null) {
            return null;
        }
        // 先取走再校验：并发重放时只有一个请求能拿到票据，其余直接失败
        ChallengeTicket ticket = store.remove(ticketId);
        if (ticket == null) {
            log.debug("票据不存在或已被消费（疑似重放）ticketId={}", ticketId);
            return null;
        }
        if (ticket.isExpired()) {
            log.debug("票据已过期 ticketId={}", ticketId);
            return null;
        }
        if (!ticket.matches(username, deviceId, ipSegment)) {
            // 上下文不匹配：票据被搬到了别的账号/设备/网络，按失效处理并留痕
            log.warn("票据上下文不匹配，已作废 ticketId={} 期望用户={}", ticketId, ticket.getUsername());
            return null;
        }
        return ticket;
    }

    @Override
    public void remove(String ticketId) {
        if (ticketId != null) {
            store.remove(ticketId);
        }
    }

    /**
     * 清理过期票据（每 10 分钟一次），避免长期运行后内存膨胀。
     */
    @Scheduled(fixedDelay = 600_000)
    public void cleanExpired() {
        int removed = 0;
        for (Map.Entry<String, ChallengeTicket> entry : store.entrySet()) {
            if (entry.getValue().isExpired()) {
                store.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("清理过期验证票据 {} 张", removed);
        }
    }
}
