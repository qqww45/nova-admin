package com.example.admin.infrastructure.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.constant.Constants;
import com.example.admin.domain.port.TokenBlacklist;
import com.example.admin.infrastructure.persistence.po.SysTokenBlacklist;
import com.example.admin.infrastructure.persistence.mapper.SysTokenBlacklistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌黑名单服务。
 *
 * <p>作用：JWT 无状态，登出后服务端无法天然让令牌失效；
 * 本服务将令牌的 {@code jti} 落库，认证过滤器校验时先查黑名单，实现“令牌可失效”。
 *
 * <p>为什么用表而不是纯内存：
 * 内存方案在应用重启后失效（用户登出后重启应用仍能使用旧令牌），
 * 表方案可保证一致性；生产环境建议替换为 Redis（TTL 与令牌剩余有效期一致，自动过期）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService implements TokenBlacklist {

    private final SysTokenBlacklistMapper tokenBlacklistMapper;

    /**
     * 已拉黑令牌的本地缓存：jti → 失效时间。
     *
     * <p>只缓存"已拉黑"的结果，<b>绝不缓存"未拉黑"</b>：一旦缓存了负结果，
     * 用户登出后的令牌在缓存窗口内仍会被判定为有效，登出就失去意义了。
     * 正结果可以安全缓存，因为黑名单只增不减，命中一次就会在剩余有效期内持续命中。
     */
    private final ConcurrentHashMap<String, LocalDateTime> blacklistCache = new ConcurrentHashMap<>();

    /**
     * 将令牌加入黑名单。
     *
     * @param jti        令牌唯一标识
     * @param tokenType  令牌类型（access / refresh）
     * @param userId     用户 ID
     * @param expireTime 令牌过期时间（过期后黑名单记录即无意义，可被清理）
     */
    public void add(String jti, String tokenType, Long userId, LocalDateTime expireTime) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        SysTokenBlacklist record = new SysTokenBlacklist();
        record.setJti(jti);
        record.setTokenType(tokenType);
        record.setUserId(userId);
        record.setExpireTime(expireTime);
        record.setCreateTime(LocalDateTime.now());
        try {
            tokenBlacklistMapper.insert(record);
            // 落库成功后才缓存：写库失败时若也缓存，本进程会以为令牌已失效，
            // 而其它实例（或重启后）依然认它 —— 行为不一致比不缓存更糟
            blacklistCache.put(jti, expireTime == null ? LocalDateTime.now().plusDays(1) : expireTime);
        } catch (Exception e) {
            // 黑名单写入失败不应阻断登出流程（最坏情况是该令牌在过期前仍可用），
            // 但必须记录日志以便排查
            log.error("令牌加入黑名单失败", e);
        }
    }

    /**
     * 判断令牌是否已被拉黑。
     *
     * @param jti 令牌唯一标识
     * @return true 表示已被拉黑
     */
    public boolean contains(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        // 先查本地缓存：已拉黑的令牌不必每次请求都回数据库查一遍
        // （黑名单是"只增不减"的，命中一次就会在剩余有效期内持续命中）
        LocalDateTime cached = blacklistCache.get(jti);
        if (cached != null) {
            if (cached.isAfter(LocalDateTime.now())) {
                return true;
            }
            // 已过有效期：令牌本身也过期了，缓存记录不再有意义
            blacklistCache.remove(jti);
        }
        Long count = tokenBlacklistMapper.selectCount(new LambdaQueryWrapper<SysTokenBlacklist>()
                .eq(SysTokenBlacklist::getJti, jti));
        return count != null && count > 0;
    }

    /**
     * 清理本地缓存中已过期的条目。
     *
     * <p>为什么不能只靠 {@link #contains} 里的惰性删除：只有被再次查询到的 jti 才会被清掉，
     * 大量"登出后再也没出现过"的条目会一直留在内存里。
     */
    @Scheduled(fixedDelay = 600_000)
    public void cleanLocalCache() {
        LocalDateTime now = LocalDateTime.now();
        int before = blacklistCache.size();
        blacklistCache.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        if (before != blacklistCache.size()) {
            log.debug("清理本地黑名单缓存 {} 条", before - blacklistCache.size());
        }
    }

    /**
     * 清理已过期的黑名单记录（每天凌晨 3 点执行）。
     *
     * <p>说明：令牌过期后即便不在黑名单也无法通过校验，因此可安全清理，避免表无限膨胀。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanExpired() {
        LocalDateTime now = LocalDateTime.now();
        // 一条 DELETE ... WHERE expire_time < ? 就够了：
        // 先 selectList 再逐条 deleteById，清理成本会随过期记录数线性增长（10 万条 = 10 万次 DELETE），
        // 而且整段都跑在同一个事务里，凌晨的定时任务会长时间持事务。
        int deleted = tokenBlacklistMapper.delete(
                new LambdaQueryWrapper<SysTokenBlacklist>().lt(SysTokenBlacklist::getExpireTime, now));
        log.info("清理过期令牌黑名单记录 {} 条", deleted);
    }

    /**
     * 退出登录：将当前访问令牌与刷新令牌一并拉黑。
     *
     * @param accessJti  访问令牌 jti
     * @param refreshJti 刷新令牌 jti（可为空）
     * @param userId     用户 ID
     * @param expireTime 令牌过期时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(String accessJti, String refreshJti, Long userId, LocalDateTime expireTime) {
        add(accessJti, Constants.TOKEN_TYPE_ACCESS, userId, expireTime);
        if (refreshJti != null && !refreshJti.isBlank()) {
            add(refreshJti, Constants.TOKEN_TYPE_REFRESH, userId, expireTime);
        }
    }
}
