package com.example.admin.common.exception;

import com.example.admin.domain.model.auth.ChallengeTicket;

/**
 * 需要人机验证（两阶段登录的中间态）。
 *
 * <p>为什么用异常而不是返回值：
 * 登录应用服务的正常返回是"令牌"，而"需要验证"是一条提前中断的路径；
 * 用异常承载可以让主流程保持"成功就返回令牌"的单一形状，
 * 同时把票据（后续步骤的凭证）一并带到接口层，由接口层决定响应码与数据结构。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class ChallengeRequiredException extends RuntimeException {

    private final ChallengeTicket ticket;

    public ChallengeRequiredException(ChallengeTicket ticket) {
        super("challenge_required");
        this.ticket = ticket;
    }

    public ChallengeTicket getTicket() {
        return ticket;
    }
}
