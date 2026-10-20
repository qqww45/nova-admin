package com.example.admin.interfaces.controller;

import com.example.admin.common.exception.ChallengeRequiredException;
import com.example.admin.common.result.R;
import com.example.admin.common.result.ResultCode;
import com.example.admin.domain.model.auth.ChallengeTicket;
import com.example.admin.domain.model.auth.RiskLevel;
import com.example.admin.interfaces.dto.req.ChallengeVerifyReq;
import com.example.admin.interfaces.dto.req.LoginReq;
import com.example.admin.interfaces.dto.req.OneTimeCodeReq;
import com.example.admin.interfaces.dto.resp.ChallengeResp;
import com.example.admin.interfaces.dto.resp.OneTimeCodeResp;
import com.example.admin.interfaces.dto.req.RefreshTokenReq;

import com.example.admin.interfaces.dto.resp.LoginResp;
import com.example.admin.interfaces.dto.resp.MenuVo;
import com.example.admin.interfaces.dto.resp.UserInfoResp;
import com.example.admin.application.dto.LoginContext;
import com.example.admin.application.service.AuthAppService;
import com.example.admin.infrastructure.util.LoginContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证控制器：登录、登出、令牌刷新、用户信息与路由。
 *
 * <p>说明：Controller 只负责参数接收与响应封装，业务逻辑全部下沉到 {@link AuthAppService}。
 * 接口级权限由 Spring Security 白名单 + 方法级注解共同控制（本控制器的登出、用户信息等接口需认证）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录、登出、令牌刷新、用户信息与菜单路由")
public class LoginController {

    private final AuthAppService authService;

    /**
     * 账号密码登录（两阶段的第一步）。
     *
     * <p>凭据校验通过后才做风险评级：无风险直接返回令牌（用户零操作）；
     * 存在风险时返回一次性票据与验证强度（1014 轻验证 / 1015 强验证），
     * 前端完成验证后调用 {@code /login/verify} 换取令牌。
     *
     * @param req     登录参数
     * @param request 请求对象（用于风控与审计）
     * @return 令牌，或需要验证时返回票据
     */
    @PostMapping("/login")
    @Operation(summary = "账号密码登录", description = "凭据校验 + 风险评级：无风险直接放行，有风险返回一次性验证票据")
    // data 有两种形态：无风险时是令牌（LoginResp），需要验证时是挑战票据（ChallengeResp），故用通配符
    public R<?> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        try {
            // 请求对象到此为止：往下传的是不含 Servlet 依赖的上下文值对象
            return R.ok(authService.login(req, LoginContextFactory.from(request)));
        } catch (ChallengeRequiredException e) {
            // 需要人机验证：把一次性票据回传给前端（票据是后续验证的唯一凭证）
            ChallengeTicket ticket = e.getTicket();
            ResultCode code = ticket.getLevel() == RiskLevel.STRONG
                    ? ResultCode.VERIFY_STRONG_REQUIRED
                    : ResultCode.VERIFY_LIGHT_REQUIRED;
            return R.fail(code, ChallengeResp.from(ticket));
        }
    }

    /**
     * 完成人机验证并登录（两阶段的第二步）。
     *
     * @param req     验证参数（票据 + 交互样本或一次性码）
     * @param request 请求对象
     * @return 令牌与用户简要信息
     */
    @PostMapping("/login/verify")
    @Operation(summary = "完成人机验证", description = "提交一次性票据与验证证据，票据原子消费（用后即焚，防重放）")
    // data 有两种形态：直接登录成功时是令牌（LoginResp），
    // 滑块通过但强验证仍需一次性码时是挑战（ChallengeResp），故用通配符
    public R<?> verifyChallenge(@Valid @RequestBody ChallengeVerifyReq req, HttpServletRequest request) {
        try {
            return R.ok(authService.verifyChallenge(req, LoginContextFactory.from(request)));
        } catch (ChallengeRequiredException e) {
            // 滑块已通过，但强验证还需要一次性码：回传同一张票据（已被标记为"人机已过"）。
            // 前端据此切到验证码输入；此时才满足发码接口的门槛，避免脚本直接触发真实邮件。
            ChallengeTicket ticket = e.getTicket();
            return R.fail(ResultCode.VERIFY_STRONG_REQUIRED, ChallengeResp.from(ticket));
        }
    }

    /**
     * 下发一次性验证码（强验证通道，同时作为无障碍降级路径）。
     *
     * @param req     请求参数
     * @param request 请求对象
     * @return 下发结果（开发模式回传验证码）
     */
    @PostMapping("/login/code")
    @Operation(summary = "下发一次性验证码", description = "强验证使用；同时作为无法完成按压验证时的降级通道")
    public R<OneTimeCodeResp> oneTimeCode(@Valid @RequestBody OneTimeCodeReq req, HttpServletRequest request) {
        return R.ok(authService.sendOneTimeCode(req, LoginContextFactory.from(request)));
    }

    /**
     * 刷新令牌（无感刷新）。
     *
     * @param req 刷新令牌参数
     * @return 新的令牌对
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用 refresh 令牌换发新的 access/refresh 令牌，旧 refresh 令牌会失效")
    public R<LoginResp> refresh(@Valid @RequestBody RefreshTokenReq req) {
        return R.ok(authService.refresh(req.getRefreshToken()));
    }

    /**
     * 退出登录。
     *
     * @param req 可选参数（携带 refreshToken 时一并拉黑）
     * @return 空响应
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "将当前令牌加入黑名单，使其立即失效")
    public R<Void> logout(@RequestBody(required = false) RefreshTokenReq req) {
        authService.logout(req == null ? null : req.getRefreshToken());
        return R.ok();
    }

    /**
     * 获取当前用户信息（角色、权限、主题）。
     *
     * @return 用户信息
     */
    @GetMapping("/getInfo")
    @Operation(summary = "获取当前用户信息", description = "包含角色标识集合、权限标识集合与主题偏好")
    public R<UserInfoResp> getInfo() {
        return R.ok(authService.getUserInfo());
    }

    /**
     * 获取当前用户的菜单树（前端动态路由）。
     *
     * @return 菜单树
     */
    @GetMapping("/getRouters")
    @Operation(summary = "获取当前用户菜单树", description = "前端据此动态生成可访问路由与侧边栏")
    public R<List<MenuVo>> getRouters() {
        return R.ok(authService.getUserRouters());
    }
}
