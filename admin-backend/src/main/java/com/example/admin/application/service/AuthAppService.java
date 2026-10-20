package com.example.admin.application.service;

import com.example.admin.application.dto.LoginContext;
import com.example.admin.interfaces.dto.req.LoginReq;

import com.example.admin.interfaces.dto.resp.LoginResp;
import com.example.admin.interfaces.dto.resp.MenuVo;
import com.example.admin.interfaces.dto.resp.UserInfoResp;

import java.util.List;

/**
 * 认证应用服务（用例编排层）：登录、登出、令牌刷新、用户信息与菜单路由。
 *
 * <p>认证是典型的"应用服务"场景：它跨用户聚合、权限领域服务、令牌基础设施、
 * 登录日志等多个协作方，自身不持有状态，只负责编排。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface AuthAppService {

    /**
     * 账号密码登录。
     *
     * @param req     登录参数
     * @param ctx     客户端上下文（IP / UA / 网段等，用于风控与审计）
     * @return 令牌与用户简要信息
     */
    LoginResp login(LoginReq req, LoginContext ctx);

    /**
     * 刷新令牌（无感刷新）。
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌对
     */
    LoginResp refresh(String refreshToken);

    /**
     * 退出登录：令牌加入黑名单。
     *
     * @param refreshToken 刷新令牌（可为空，传入时一并拉黑）
     */
    void logout(String refreshToken);

    /**
     * 完成人机验证并登录（两阶段登录的第二阶段）。
     *
     * <p>票据在这里被原子消费：无论成功或失败，同一张票据都不可能再被使用一次，
     * 这是抗重放的关键；验证失败与密码失败计入同一套计数，攻击者无法靠"反复试验证"绕过风控。
     *
     * @param req     验证参数（票据 + 交互样本或一次性码）
     * @param ctx     客户端上下文
     * @return 令牌与用户信息
     */
    LoginResp verifyChallenge(com.example.admin.interfaces.dto.req.ChallengeVerifyReq req,
                              LoginContext ctx);

    /**
     * 下发一次性验证码（强验证用）。
     *
     * @param req     请求参数（票据 + 用户名 + 设备）
     * @param ctx     客户端上下文
     * @return 下发结果（开发模式会回传验证码，便于自测）
     */
    com.example.admin.interfaces.dto.resp.OneTimeCodeResp sendOneTimeCode(
            com.example.admin.interfaces.dto.req.OneTimeCodeReq req,
            LoginContext ctx);

    /**
     * 强制某用户全部设备下线（服务端主动踢人）。
     *
     * <p>实现：删除该用户所有设备的 refresh 令牌 + 撤销可信设备记录，
     * 使其所有设备在下一次刷新令牌时失败，必须重新登录并重新验证。
     *
     * @param userId 用户 ID
     * @return 被下线的设备数量
     */
    int kick(Long userId);

    /**
     * 获取当前登录用户信息（含角色、权限、主题）。
     *
     * @return 用户信息
     */
    UserInfoResp getUserInfo();

    /**
     * 获取当前用户的菜单树（前端动态路由）。
     *
     * @return 菜单树
     */
    List<MenuVo> getUserRouters();
}
