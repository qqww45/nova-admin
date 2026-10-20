package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.ChallengeTicket;

/**
 * 验证票据存储（领域接口）。
 *
 * <p>关键约束：消费必须是原子的 —— 校验、作废、返回在同一个操作内完成，
 * 否则并发重放（同时提交两次同一票据）会两次都通过。
 *
 * <p>Redis 实现建议：
 * <pre>
 * SET challenge:{ticketId} {票据 JSON} NX EX 180
 * 消费：GETDEL（Redis 6.2+）或用 Lua 脚本保证"读取 + 删除"原子性
 * </pre>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ChallengeStore {

    /**
     * 保存票据。
     *
     * @param ticket 票据
     */
    void save(ChallengeTicket ticket);

    /**
     * 查询票据（不消费，用于发送一次性码等只读场景）。
     *
     * @param ticketId 票据 ID
     * @return 票据，不存在或已过期返回 null
     */
    ChallengeTicket get(String ticketId);

    /**
     * 原子消费票据：校验绑定上下文、有效期，通过后立即作废。
     *
     * @param ticketId   票据 ID
     * @param username   用户名
     * @param deviceId   设备标识
     * @param ipSegment  来源网段
     * @return 消费成功返回票据；票据不存在、已过期、上下文不匹配或已被消费时返回 null
     */
    ChallengeTicket consume(String ticketId, String username, String deviceId, String ipSegment);

    /**
     * 作废票据。
     *
     * @param ticketId 票据 ID
     */
    void remove(String ticketId);
}
