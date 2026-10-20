package com.example.admin.domain.port;

import com.example.admin.domain.model.auth.LoginUser;

/**
 * 当前操作者（谁在调用）端口。
 *
 * <p>存在理由：应用层原先直接调用 {@code infrastructure.util.SecurityUtils} 拿当前登录人。
 * 静态工具类看着方便，代价是应用层的每个用例都被钉死在 Spring Security 的
 * {@code SecurityContextHolder} 上：离线任务、消息消费、单元测试都进不来。
 * "当前是谁"这个问题属于应用逻辑（决定能否改这个账号、能否授这个权限），
 * 而"从哪儿读出来"属于基础设施，所以这里只声明能力，实现放在基础设施层。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface OperatorContext {

    /**
     * 当前操作者（未登录或上下文不可用时返回 null）。
     *
     * @return 登录用户信息，可能为 null
     */
    LoginUser current();

    /**
     * 当前操作者 ID。
     *
     * @return 用户 ID，未登录时返回 null
     */
    Long currentUserIdOrNull();

    /**
     * 当前操作者 ID（未登录时抛业务异常，适用于"必须有登录人"的用例）。
     *
     * @return 用户 ID
     */
    Long currentUserId();
}
