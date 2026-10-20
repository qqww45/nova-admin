package com.example.admin.application.service.impl;

import com.example.admin.application.dto.LoginContext;
import com.example.admin.application.service.AuthAppService;
import com.example.admin.application.service.LoginLogAppService;
import com.example.admin.application.service.MenuAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.exception.ChallengeRequiredException;
import com.example.admin.domain.model.auth.ChallengeTicket;
import com.example.admin.domain.model.auth.DeviceFingerprint;
import com.example.admin.domain.model.auth.SlideSample;
import com.example.admin.domain.model.auth.LoginRiskSignal;
import com.example.admin.domain.model.auth.OnlineSession;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.auth.RiskLevel;
import com.example.admin.domain.model.loginlog.LoginLog;
import com.example.admin.domain.model.user.User;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.domain.repository.UserRepository;
import com.example.admin.domain.service.ChallengeStore;
import com.example.admin.domain.service.DeviceTrustPolicy;
import com.example.admin.domain.service.HumanInteractionVerifier;
import com.example.admin.domain.service.LoginPolicy;
import com.example.admin.domain.service.LoginRiskPolicy;
import com.example.admin.domain.model.auth.OneTimeCodeMessage;
import com.example.admin.domain.service.OneTimeCodeSender;
import com.example.admin.domain.service.PasswordHasher;
import com.example.admin.domain.service.PermissionDomainService;
import com.example.admin.domain.service.OnlineSessionStore;
import com.example.admin.domain.service.RefreshTokenStore;

import com.example.admin.domain.exception.InvalidTokenException;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.port.TokenBlacklist;
import com.example.admin.domain.port.TokenClaims;
import com.example.admin.domain.port.TokenIssuer;
import com.example.admin.interfaces.dto.req.ChallengeVerifyReq;
import com.example.admin.interfaces.dto.req.LoginReq;
import com.example.admin.interfaces.dto.req.OneTimeCodeReq;

import com.example.admin.interfaces.dto.resp.LoginResp;
import com.example.admin.interfaces.dto.resp.OneTimeCodeResp;
import com.example.admin.interfaces.dto.resp.MenuVo;
import com.example.admin.interfaces.dto.resp.UserInfoResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证应用服务实现（登录用例编排）。
 *
 * <p>安全设计：
 * <ul>
 *     <li>登录失败统一提示"用户名或密码错误"，不区分用户是否存在，防止账号枚举；</li>
 *     <li>防护顺序：IP 限流 → 账号锁定 → 设备信任判定 → 二次校验 → 密码校验，逐层收窄攻击面；</li>
 *     <li><b>可信设备免验证码</b>：已信任设备直接放行，陌生设备才要求验证码（低打扰）；</li>
 *     <li><b>双 Token + 轮换</b>：refresh 令牌按"用户 + 设备"维度只保留当前有效的一个，
 *         每次刷新都覆盖并拉黑旧的，旧令牌即便泄露也只能生效一次；</li>
 *     <li>登录成功/失败均写入审计日志；密码与令牌绝不出现在日志中。</li>
 * </ul>
 *
 * <p>参考思路：后端分层与权限模型参考 RuoYi-Vue-Plus，令牌轮换与设备信任为本项目实现。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAppServiceImpl implements AuthAppService {

    /** 验证票据有效期（秒）：短时效是压缩攻击窗口的第一道手段 */
    private static final long CHALLENGE_TTL_SECONDS = 180L;

    /** 一次性验证码有效期（秒） */
    /** 一次性码有效期（秒）：与邮件正文提示共用一处配置，避免"说 5 分钟实则 10 分钟" */
    @Value("${admin.verify.one-time-code-ttl-seconds:300}")
    private long oneTimeCodeTtlSeconds;

    /** 同一张票据允许的一次性码尝试次数（超过即作废，防止码被爆破） */
    private static final int MAX_CODE_ATTEMPTS = 3;

    /** 通过人机验证后，单张票据最多可下发验证码次数（"已过滑块 + 换码重发"要留余地，但不能无限） */
    private static final int MAX_CODE_SEND_PER_TICKET = 3;

    /**
     * 未通过人机验证（无障碍降级通道）时单张票据最多可下发次数。
     *
     * <p>为什么要比 {@link #MAX_CODE_SEND_PER_TICKET} 更紧：这条路径绕过了人机门槛，
     * 是自动化脚本唯一能直接触发真实邮件的地方。它必须保留（否则依赖辅助设备的用户无法登录），
     * 但额度要压到"够真人用、不够脚本刷"。
     */
    private static final int MAX_CODE_SEND_FALLBACK = 2;

    /** 两次验证码下发之间的冷却秒数：既是防轰炸，也避免用户连点把邮箱灌满 */
    private static final long CODE_SEND_COOLDOWN_SECONDS = 60L;

    /** 验证码随机数发生器（不用 Random，避免可被预测） */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /** 滑块缺口可出现的区间（像素）：画布宽 300、拼图块宽 48，两端留出余量避免贴边 */
    private static final int SLIDER_MIN_X = 40;

    private static final int SLIDER_MAX_X = 252;

    private final LoginPolicy loginPolicy;

    private final PasswordHasher passwordHasher;

    private final TokenIssuer tokenIssuer;

    private final TokenBlacklist tokenBlacklist;

    private final OperatorContext operatorContext;

    private final DeviceTrustPolicy deviceTrustPolicy;

    private final RefreshTokenStore refreshTokenStore;

    private final OnlineSessionStore onlineSessionStore;

    private final UserRepository userRepository;

    private final DeptRepository deptRepository;

    private final MenuAppService menuAppService;

    private final PermissionDomainService permissionDomainService;

    private final LoginLogAppService loginLogAppService;

    private final LoginRiskPolicy loginRiskPolicy;

    private final ChallengeStore challengeStore;

    private final HumanInteractionVerifier humanInteractionVerifier;

    private final OneTimeCodeSender oneTimeCodeSender;

    /**
     * 账号密码登录。
     *
     * @param req     登录参数
     * @param request 请求对象
     * @return 令牌与用户信息
     */
    @Override
    public LoginResp login(LoginReq req, LoginContext ctx) {
        String ip = ctx.ip();
        String browser = ctx.browser();
        String os = ctx.os();

        // 1. IP 级限流（防脚本暴力破解）
        checkPolicy(() -> loginPolicy.checkIpLimit(ip), "登录过于频繁，请稍后再试");
        // 2. 账号锁定检查
        checkPolicy(() -> loginPolicy.checkAccountLocked(req.getUsername()), "账号已被锁定，请稍后再试");

        // 3. 设备识别：优先使用前端持久化的 deviceId，缺失时按 UA + IP 段 + 指纹推导
        String deviceId = resolveDeviceId(req, ctx);
        String ipSegment = ctx.ipSegment();

        // 4. 校验账号与密码（用户不存在与密码错误返回相同提示，防账号枚举）
        User user = userRepository.findByUsername(req.getUsername()).orElse(null);
        if (user == null) {
            handleFailure(req.getUsername(), ip, browser, os, "用户不存在");
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!user.isEnabled()) {
            handleFailure(req.getUsername(), ip, browser, os, "账号已停用");
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (!passwordHasher.matches(req.getPassword(), user.getPasswordHash())) {
            handleFailure(req.getUsername(), ip, browser, os, "密码错误");
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 5. 凭据正确之后才做风险评级 —— 顺序很重要：
        //    验证环节只在"账号密码都对"之后才出现，因此攻击者无法用"是否弹出验证"来探测账号是否存在；
        //    而凭据错误的每一次尝试都会计入失败次数，同样受锁定与限流约束。
        RiskLevel level = loginRiskPolicy.evaluate(new LoginRiskSignal(
                user.getUsername(),
                deviceId,
                deviceTrustPolicy.isTrusted(user.getId(), deviceId),
                loginPolicy.failureCount(user.getUsername()),
                isAutomationSuspected(ctx)));

        if (level != RiskLevel.NONE) {
            // 需要验证：签发一次性票据（绑定账号 + 设备 + 网段），由接口层转换为 1014 / 1015
            throw new ChallengeRequiredException(issueTicket(user.getUsername(), deviceId, ipSegment, level));
        }

        // 6. 无风险：直接完成登录（用户零操作 —— 这就是"无感通过"）
        return completeLogin(user, deviceId, ip, browser, os, !Boolean.FALSE.equals(req.getTrustDevice()));
    }

    /**
     * 完成人机验证并登录（两阶段登录的第二阶段）。
     *
     * <p>票据在这里被<b>原子消费</b>：无论成功还是失败，同一张票据都不可能再被用第二次，
     * 重放与并发双花在这一步就被挡掉；验证失败与密码错误计入同一套计数，
     * 因此"反复刷验证"与"反复试密码"一样会触发锁定。
     *
     * @param req     验证参数（票据 + 交互样本 或 一次性码）
     * @param request 请求对象
     * @return 令牌与用户信息
     */
    @Override
    public LoginResp verifyChallenge(ChallengeVerifyReq req, LoginContext ctx) {
        String ip = ctx.ip();
        String browser = ctx.browser();
        String os = ctx.os();

        String deviceId = req.getDeviceId() != null && !req.getDeviceId().isBlank()
                ? req.getDeviceId()
                : deviceTrustPolicy.resolveDeviceId(new DeviceFingerprint(
                        ctx.userAgent(), ip, req.getFingerprint()));
        String ipSegment = ctx.ipSegment();

        // 两类证据走不同分支：带一次性码 → 码校验；否则 → 人机验证（滑块）
        return req.getCode() == null || req.getCode().isBlank()
                ? verifyByHumanInteraction(req, ip, browser, os, deviceId, ipSegment)
                : verifyByOneTimeCode(req, ip, browser, os, deviceId, ipSegment);
    }

    /**
     * 人机验证（滑块）分支。
     *
     * <p>强验证票据在这里<b>不被消费</b>：滑块通过后只把票据标记为"人机已过"，
     * 后续还要用同一张票据去校验一次性码。若这里就消费掉，强验证会退化成
     * "滑块或码二选一"，"发码前先过人机"这道门槛也就不存在了。
     */
    private LoginResp verifyByHumanInteraction(ChallengeVerifyReq req, String ip, String browser,
                                               String os, String deviceId, String ipSegment) {
        ChallengeTicket ticket = challengeStore.get(req.getTicketId());
        if (ticket == null || ticket.isExpired() || !ticket.matches(req.getUsername(), deviceId, ipSegment)) {
            handleFailure(req.getUsername(), ip, browser, os, "验证票据无效或已过期");
            throw new BusinessException(ResultCode.CHALLENGE_INVALID);
        }
        boolean passed = humanInteractionVerifier.verify(new SlideSample(
                req.getTrack(),
                req.getSlideX() == null ? -1 : req.getSlideX(),
                ticket.getExpectedX(),
                req.getDurationMs() == null ? 0L : req.getDurationMs(),
                req.getStartDelayMs() == null ? 0L : req.getStartDelayMs(),
                req.getElapsedMs() == null ? 0L : req.getElapsedMs()));
        if (!passed) {
            // 失败即作废：不给"无限重试滑块"的机会，重试必须重新走完整登录
            // （重新登录会再次经过账号锁定与 IP 限流，脚本无法靠重试积累成功率）
            challengeStore.remove(req.getTicketId());
            handleFailure(req.getUsername(), ip, browser, os, "人机验证未通过");
            throw new BusinessException(ResultCode.CHALLENGE_INVALID, "验证未通过，请重新登录");
        }
        if (ticket.getLevel() == RiskLevel.STRONG) {
            // 强验证：滑块只是"发码前的门槛"，通过后进入一次性码阶段
            ticket.markHumanVerified();
            challengeStore.save(ticket);
            log.info("人机验证通过，进入一次性码阶段 username={}", req.getUsername());
            throw new ChallengeRequiredException(ticket);
        }
        // 轻验证：滑块通过即完成登录，原子消费票据（用后即焚）
        if (challengeStore.consume(req.getTicketId(), req.getUsername(), deviceId, ipSegment) == null) {
            handleFailure(req.getUsername(), ip, browser, os, "验证票据无效或已被使用（疑似重放）");
            throw new BusinessException(ResultCode.CHALLENGE_INVALID);
        }
        return finishLogin(req.getUsername(), deviceId, ip, browser, os, req.getTrustDevice());
    }

    /**
     * 一次性码分支。
     *
     * <p>前置条件：必须先通过滑块（{@code humanVerified}），或走过无障碍降级通道
     * （{@code sendWithoutHumanVerify > 0}）。两者都不满足却直接来提交码，
     * 说明是脚本在绕过人机环节直接撞码 —— 直接作废票据。
     */
    private LoginResp verifyByOneTimeCode(ChallengeVerifyReq req, String ip, String browser,
                                          String os, String deviceId, String ipSegment) {
        ChallengeTicket ticket = challengeStore.get(req.getTicketId());
        if (ticket == null || ticket.isExpired() || !ticket.matches(req.getUsername(), deviceId, ipSegment)) {
            handleFailure(req.getUsername(), ip, browser, os, "验证票据无效或已过期");
            throw new BusinessException(ResultCode.CHALLENGE_INVALID);
        }
        if (ticket.getLevel() != RiskLevel.STRONG) {
            throw new BusinessException(ResultCode.CHALLENGE_INVALID, "当前验证方式不正确");
        }
        if (!ticket.isHumanVerified() && ticket.getSendWithoutHumanVerify() == 0) {
            challengeStore.remove(req.getTicketId());
            handleFailure(req.getUsername(), ip, browser, os, "未通过人机验证即提交一次性码（疑似脚本）");
            throw new BusinessException(ResultCode.CHALLENGE_INVALID, "请先完成滑块验证");
        }

        ticket.increaseCodeAttempts();
        boolean matched = ticket.getOneTimeCode() != null && MessageDigest.isEqual(
                ticket.getOneTimeCode().getBytes(StandardCharsets.UTF_8),
                req.getCode().getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            if (ticket.isCodeAttemptsExhausted(MAX_CODE_ATTEMPTS)) {
                challengeStore.remove(req.getTicketId());
                handleFailure(req.getUsername(), ip, browser, os, "一次性验证码尝试次数超限");
                throw new BusinessException(ResultCode.CHALLENGE_INVALID, "验证码错误次数过多，请重新登录");
            }
            // 未超限：保留票据并回写次数，输错一次不必打回登录页重来
            // （此前 MAX_CODE_ATTEMPTS 是死代码 —— 票据在校验前就被 consume 删掉了，
            //   计数永远加不到上限；现在改成"失败不销毁、超限才销毁"，这条限制才真正生效）
            challengeStore.save(ticket);
            handleFailure(req.getUsername(), ip, browser, os, "一次性验证码错误");
            throw new BusinessException(ResultCode.CHALLENGE_INVALID, "验证码不正确");
        }
        if (challengeStore.consume(req.getTicketId(), req.getUsername(), deviceId, ipSegment) == null) {
            handleFailure(req.getUsername(), ip, browser, os, "验证票据无效或已被使用（疑似重放）");
            throw new BusinessException(ResultCode.CHALLENGE_INVALID);
        }
        return finishLogin(req.getUsername(), deviceId, ip, browser, os, req.getTrustDevice());
    }

    /**
     * 校验通过后的收尾：加载用户 → 完成登录。
     */
    private LoginResp finishLogin(String username, String deviceId, String ip, String browser,
                                  String os, Boolean trustDevice) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR));
        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        return completeLogin(user, deviceId, ip, browser, os, !Boolean.FALSE.equals(trustDevice));
    }

    /**
     * 下发一次性验证码（强验证通道）。
     *
     * @param req     请求参数（票据 + 用户名 + 设备）
     * @param request 请求对象
     * @return 下发结果（开发模式会回传验证码，便于自测）
     */
    @Override
    public OneTimeCodeResp sendOneTimeCode(OneTimeCodeReq req, LoginContext ctx) {
        String ip = ctx.ip();
        String ipSegment = ctx.ipSegment();
        String deviceId = req.getDeviceId() == null ? "" : req.getDeviceId();

        // 1. IP 限流：此前这个接口完全没有限流门槛，只要拿到票据就能循环触发真实邮件 ——
        //    弱口令场景下等于把目标用户的邮箱当成靶子（邮件轰炸），还会耗尽通道配额。
        //    与登录共用同一套 IP 计数，脚本无法靠换票据绕开。
        checkPolicy(() -> loginPolicy.checkIpLimit(ip), "请求过于频繁，请稍后再试");

        // 2. 票据必须存在且上下文匹配（防止拿别人的票据去触发短信/邮件轰炸）
        ChallengeTicket ticket = challengeStore.get(req.getTicketId());
        if (ticket == null || ticket.isExpired() || !ticket.matches(req.getUsername(), deviceId, ipSegment)) {
            throw new BusinessException(ResultCode.CHALLENGE_INVALID);
        }
        if (ticket.getLevel() == RiskLevel.LIGHT) {
            // 无障碍降级：用户无法完成按压/滑动（如使用辅助设备、控件不可用）时，
            // 把轻验证升级为一次性码，并换发新票据（旧票据立即作废）。
            // 这条路径绕过人机门槛，因此后面的额度按更紧的 FALLBACK 口径计算。
            challengeStore.remove(ticket.getTicketId());
            ticket = issueTicket(req.getUsername(), deviceId, ipSegment, RiskLevel.STRONG);
            log.info("轻验证降级为一次性码 username={}", req.getUsername());
        }

        // 3. 额度与冷却：是否通过人机验证决定用哪套额度
        boolean viaHumanVerify = ticket.isHumanVerified();
        int maxCount = viaHumanVerify ? MAX_CODE_SEND_PER_TICKET : MAX_CODE_SEND_FALLBACK;
        if (!ticket.canSendCode(CODE_SEND_COOLDOWN_SECONDS, maxCount)) {
            if (ticket.getCodeSendCount() >= maxCount) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT,
                        "验证码下发次数已达上限，请重新登录后再试");
            }
            long waitSeconds = ticket.nextSendWaitSeconds(CODE_SEND_COOLDOWN_SECONDS);
            throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT,
                    "验证码下发过于频繁，请 " + waitSeconds + " 秒后再试");
        }

        String code = generateOneTimeCode();
        ticket.setOneTimeCode(code);
        // 记录本次下发（用于额度与冷却判断），与码值一起回写
        ticket.noteCodeSent(viaHumanVerify);
        // 必须显式回写：存储返回的是"快照"而非对象引用。
        // 内存实现下 setOneTimeCode 会隐式生效（拿到的是同一个对象），
        // 但换成 Redis 后是反序列化副本，不回写就等于"码生成了却没存进去"，校验必然失败。
        // 这里按与存储无关的语义写，两种实现行为一致。
        challengeStore.save(ticket);

        // 先查用户再下发：邮件/短信通道需要拿到真实联系方式，
        // 若账号未绑定联系方式应直接报错，而不是"发送成功"却谁也收不到
        User user = userRepository.findByUsername(req.getUsername()).orElse(null);
        oneTimeCodeSender.send(OneTimeCodeMessage.builder()
                .username(req.getUsername())
                .code(code)
                .email(user == null ? null : user.getEmail())
                .phone(user == null ? null : user.getPhone())
                .build());

        return OneTimeCodeResp.builder()
                .ticketId(ticket.getTicketId())
                .expireSeconds(oneTimeCodeTtlSeconds)
                .target(user == null ? "已绑定的联系方式" : maskTarget(user))
                // 仅开发模式回传验证码，便于自测；生产由 admin.verify.reveal-code=false 关闭
                .devCode(oneTimeCodeSender.isRevealCode() ? code : null)
                .build();
    }

    /**
     * 刷新令牌：校验后换发新令牌对，并轮换旧 refresh 令牌（防止长期盗用）。
     *
     * <p>三重校验：签名与有效期（JWT）→ 是否已被拉黑 → 是否仍是该设备"当前有效"的令牌。
     * 最后一项是轮换能生效的关键：旧的 refresh 令牌在存储中已被覆盖，比对必然失败。
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌对
     */
    @Override
    public LoginResp refresh(String refreshToken) {
        TokenClaims claims;
        try {
            claims = tokenIssuer.parse(refreshToken);
        } catch (InvalidTokenException e) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "刷新令牌无效或已过期，请重新登录");
        }
        // 必须是 refresh 类型：禁止用 access 令牌刷新（缩短攻击窗口）
        if (!Constants.TOKEN_TYPE_REFRESH.equals(claims.type())) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "令牌类型不正确");
        }
        String jti = claims.jti();
        if (tokenBlacklist.contains(jti)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "刷新令牌已失效，请重新登录");
        }

        // JWT 的 subject 存放用户名
        User user = userRepository.findByUsername(claims.subject())
                .orElseThrow(() -> new BusinessException(ResultCode.TOKEN_INVALID, "用户不存在，请重新登录"));
        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 设备维度校验：无 deviceId 的令牌一律拒绝。
        // 此前"deviceId 为空时跳过校验"是纵深防御的缺口：这种令牌既无法与存储中的值比对，
        // 也不会被写入存储（issueTokens 里同样跳过），7 天内可无限重放且无法被踢下线。
        // 设备标识直接取自已解析的令牌内容，不必再解析一遍
        String deviceId = claims.deviceId();
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }

        // 令牌轮换：旧 refresh 立即失效（即便后续轮换失败，它也已经进黑名单了）
        tokenBlacklist.add(jti, Constants.TOKEN_TYPE_REFRESH, user.getId(),
                LocalDateTime.now().plusSeconds(tokenIssuer.refreshTokenTtlSeconds()));

        // 原子轮换：校验"仍是当前令牌"与"写入新令牌"一步完成。
        // 分两步做的话，并发刷新会双双通过校验并互相覆盖，先返回的令牌随即失效。
        String newAccessToken = tokenIssuer.generateAccessToken(user.getId(), user.getUsername());
        String newRefreshToken = tokenIssuer.generateRefreshToken(user.getId(), user.getUsername(), deviceId);
        String newRefreshJti = tokenIssuer.jtiOf(newRefreshToken);
        boolean rotated = refreshTokenStore.rotate(user.getId(), deviceId, refreshToken, newRefreshToken,
                newRefreshJti, Duration.ofSeconds(tokenIssuer.refreshTokenTtlSeconds()));
        if (!rotated) {
            // 说明这个 refresh 令牌已经不是该设备的当前令牌：要么已被用过（重放），要么已被踢下线
            log.warn("刷新令牌轮换失败，疑似重放或已下线 userId={} deviceId={}", user.getId(), deviceId);
            throw new BusinessException(ResultCode.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }

        LoginResp resp = LoginResp.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenIssuer.accessTokenTtlSeconds())
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .nickname(user.getNickname())
                .deviceId(deviceId)
                .trusted(true)
                .build();
        // 刷新即活跃：同步会话的活跃时间与最新 access 令牌标识（旧令牌已被拉黑）
        OnlineSession session = onlineSessionStore.get(user.getId(), deviceId);
        if (session != null) {
            session.updateAccessJti(tokenIssuer.jtiOf(resp.getAccessToken()));
            session.touch();
            onlineSessionStore.save(session);
        }
        return resp;
    }

    /**
     * 退出登录：当前访问令牌（及可选刷新令牌）加入黑名单，并移除该设备的有效刷新令牌。
     *
     * @param refreshToken 刷新令牌（可为空）
     */
    @Override
    public void logout(String refreshToken) {
        LoginUser loginUser = operatorContext.current();
        if (loginUser == null) {
            return;
        }
        tokenBlacklist.add(loginUser.getTokenId(), Constants.TOKEN_TYPE_ACCESS, loginUser.getUserId(),
                LocalDateTime.now().plusSeconds(tokenIssuer.accessTokenTtlSeconds()));
        // 设备标识有两个来源：前端传来的 refreshToken（最准确），或当前登录上下文里记录的设备。
        // 此前"前端不传 refreshToken 就整段跳过"是个真问题 —— 该参数是可选的，
        // 只要请求里不带，refresh 令牌与在线会话都不会被清理：用户看到的是"已退出登录"，
        // 而攻击者手里此前窃取的 refresh 令牌（7 天有效）仍能继续换发全新令牌对。
        // 因此这里必须兜底到上下文中的设备标识，让"清理 refresh"成为登出的默认动作。
        String deviceId = null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                TokenClaims claims = tokenIssuer.parse(refreshToken);
                tokenBlacklist.add(claims.jti(), Constants.TOKEN_TYPE_REFRESH, loginUser.getUserId(),
                        LocalDateTime.now().plusSeconds(tokenIssuer.refreshTokenTtlSeconds()));
                deviceId = claims.deviceId();
            } catch (InvalidTokenException e) {
                // 刷新令牌本身已失效时无需处理
                log.debug("登出时刷新令牌解析失败，忽略：{}", e.getMessage());
            }
        }
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = loginUser.getDeviceId();
        }
        if (deviceId != null && !deviceId.isBlank()) {
            // 移除该设备的有效 refresh 令牌：此后该设备无法再无感刷新
            refreshTokenStore.remove(loginUser.getUserId(), deviceId);
            onlineSessionStore.remove(loginUser.getUserId(), deviceId);
        } else {
            log.warn("登出时无法确定设备标识，仅拉黑了当前访问令牌 user={}", loginUser.getUsername());
        }
        log.info("用户 {} 已退出登录", loginUser.getUsername());
    }

    /**
     * 登录收尾：写入信任 → 清除风控计数 → 更新登录信息 → 审计日志 → 签发令牌。
     *
     * <p>抽成一个方法的原因：无感登录与"验证通过后登录"的收尾完全一致，
     * 如果两处各写一遍，将来新增步骤（如登录提醒）很容易漏改其中一处。
     *
     * @param user     用户
     * @param deviceId 设备标识
     * @param ip       IP
     * @param browser  浏览器
     * @param os       操作系统
     * @param trustOn  是否写入设备信任
     * @return 登录响应
     */
    private LoginResp completeLogin(User user, String deviceId, String ip, String browser, String os, boolean trustOn) {
        if (trustOn) {
            deviceTrustPolicy.trust(user.getId(), deviceId);
        }
        loginPolicy.onLoginSuccess(user.getUsername());
        user.recordLogin(ip, LocalDateTime.now());
        userRepository.save(user);
        loginLogAppService.save(LoginLog.success(user.getUsername(), ip, browser, os));
        log.info("用户 {} 登录成功，IP={} deviceId={} trustOn={}", user.getUsername(), ip, deviceId, trustOn);

        LoginResp resp = issueTokens(permissionDomainService.buildLoginUser(user), deviceId, true);
        // 记录在线会话：保存当前 access 令牌的 jti，强制下线时才能立即拉黑而不是等它自然过期
        onlineSessionStore.save(new OnlineSession(user.getId(), user.getUsername(), user.getNickname(),
                deviceId, ip, browser, os, tokenIssuer.jtiOf(resp.getAccessToken())));
        return resp;
    }

    /**
     * 签发一次性验证票据（绑定账号 + 设备 + 网段）。
     *
     * @param username  用户名
     * @param deviceId  设备标识
     * @param ipSegment 来源网段
     * @param level     要求的验证强度
     * @return 票据
     */
    private ChallengeTicket issueTicket(String username, String deviceId, String ipSegment, RiskLevel level) {
        // 缺口位置由服务端随机生成并绑定到票据上：前端只负责按它渲染，
        // 因此"正确答案"只存在于服务端，前端与中间人都无法自行指定落点
        int expectedX = SLIDER_MIN_X + SECURE_RANDOM.nextInt(SLIDER_MAX_X - SLIDER_MIN_X);
        ChallengeTicket ticket = new ChallengeTicket(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                username,
                deviceId,
                ipSegment,
                expectedX,
                level,
                LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(CHALLENGE_TTL_SECONDS));
        challengeStore.save(ticket);
        log.info("下发人机验证票据 username={} level={} ticketId={}", username, level, ticket.getTicketId());
        return ticket;
    }

    /**
     * 判断请求是否疑似自动化（提升风险等级的信号之一）。
     *
     * <p>只看两类最廉价也最可靠的信号：缺少 User-Agent（脚本的典型特征）、
     * UA 中带有明显的无头浏览器标识。这是"提高成本"的一层，不是唯一防线。
     *
     * @param request 请求对象
     * @return true 表示疑似自动化
     */
    private boolean isAutomationSuspected(LoginContext ctx) {
        String ua = ctx.userAgent();
        if (ua == null || ua.isBlank()) {
            return true;
        }
        String lower = ua.toLowerCase();
        return lower.contains("headless")
                || lower.contains("selenium")
                || lower.contains("phantom")
                || lower.contains("playwright")
                || lower.contains("puppeteer");
    }

    /**
     * 生成 6 位数字一次性码。
     *
     * @return 验证码
     */
    private String generateOneTimeCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 脱敏展示验证码的发送目标（邮箱或手机号）。
     *
     * @param user 用户
     * @return 脱敏后的目标描述
     */
    private String maskTarget(User user) {
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            int at = email.indexOf('@');
            String name = email.substring(0, at);
            return (name.length() <= 1 ? name : name.charAt(0) + "***") + email.substring(at);
        }
        String phone = user.getPhone();
        if (phone != null && phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "已绑定的联系方式";
    }

    /**
     * 强制某用户全部设备下线（服务端主动踢人）。
     *
     * @param userId 用户 ID
     * @return 被下线的设备数量
     */
    @Override
    public int kick(Long userId) {
        // 先把所有设备的 access 令牌拉黑：否则被踢的用户在 access 剩余有效期内仍能继续操作
        LocalDateTime accessExpire = LocalDateTime.now().plusSeconds(tokenIssuer.accessTokenTtlSeconds());
        for (OnlineSession session : onlineSessionStore.listByUser(userId)) {
            tokenBlacklist.add(session.getAccessJti(), Constants.TOKEN_TYPE_ACCESS, userId, accessExpire);
        }
        int devices = refreshTokenStore.removeByUser(userId);
        onlineSessionStore.removeByUser(userId);
        deviceTrustPolicy.revokeAll(userId);
        log.warn("用户 {} 已被强制下线，涉及设备 {} 台", userId, devices);
        return devices;
    }

    /**
     * 获取当前登录用户信息（含角色、权限、主题）。
     *
     * @return 用户信息
     */
    @Override
    public UserInfoResp getUserInfo() {
        LoginUser loginUser = operatorContext.current();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User user = userRepository.findById(loginUser.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND));

        UserInfoResp.UserInfo info = new UserInfoResp.UserInfo();
        info.setId(String.valueOf(user.getId()));
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setSex(user.getSex());
        info.setStatus(user.getStatus());
        info.setTheme(user.getTheme());
        info.setDeptName(deptRepository.findNameById(user.getDeptId()));
        info.setLoginIp(user.getLoginIp());
        info.setLoginDate(user.getLoginDate());
        info.setCreateTime(user.getCreateTime());
        info.setRemark(user.getRemark());

        UserInfoResp resp = new UserInfoResp();
        resp.setUser(info);
        resp.setRoles(List.copyOf(loginUser.getRoles()));
        resp.setPermissions(List.copyOf(loginUser.getPermissions()));
        return resp;
    }

    /**
     * 获取当前用户的菜单树（前端动态路由数据源）。
     *
     * @return 菜单树
     */
    @Override
    public List<MenuVo> getUserRouters() {
        return menuAppService.buildUserMenuTree(operatorContext.currentUserId());
    }

    /**
     * 解析设备标识。
     *
     * @param req     登录参数（可能携带前端生成的 deviceId 与指纹）
     * @param request 请求对象
     * @param ip      客户端 IP
     * @return 设备标识
     */
    private String resolveDeviceId(LoginReq req, LoginContext ctx) {
        if (req.getDeviceId() != null && !req.getDeviceId().isBlank()) {
            return req.getDeviceId();
        }
        String fingerprint = req.getFingerprint() != null && !req.getFingerprint().isBlank()
                ? req.getFingerprint()
                : ctx.fingerprint();
        return deviceTrustPolicy.resolveDeviceId(new DeviceFingerprint(ctx.userAgent(), ctx.ip(), fingerprint));
    }

    /**
     * 签发令牌对，并把 refresh 令牌按"用户 + 设备"维度存一份（覆盖即轮换）。
     *
     * @param loginUser 登录用户（含角色与权限）
     * @param deviceId  设备标识
     * @param trusted   该设备是否已可信
     * @return 登录响应
     */
    private LoginResp issueTokens(LoginUser loginUser, String deviceId, boolean trusted) {
        String accessToken = tokenIssuer.generateAccessToken(loginUser.getUserId(), loginUser.getUsername());
        String refreshToken = tokenIssuer.generateRefreshToken(loginUser.getUserId(), loginUser.getUsername(), deviceId);
        // 把设备标识带进登录上下文：登出时前端若不传 refreshToken，
        // 服务端仍能按"当前会话所属设备"清理刷新令牌与在线会话（否则登出会漏掉这一半）。
        loginUser.setDeviceId(deviceId);
        if (deviceId != null && !deviceId.isBlank()) {
            refreshTokenStore.save(loginUser.getUserId(), deviceId, tokenIssuer.jtiOf(refreshToken), refreshToken,
                    Duration.ofSeconds(tokenIssuer.refreshTokenTtlSeconds()));
        }
        return LoginResp.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenIssuer.accessTokenTtlSeconds())
                .userId(String.valueOf(loginUser.getUserId()))
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .deviceId(deviceId)
                .trusted(trusted)
                .build();
    }

    /**
     * 登录失败统一处理（累计失败次数 + 审计日志）。
     *
     * @param username 用户名
     * @param ip       IP
     * @param browser  浏览器
     * @param os       操作系统
     * @param reason   失败原因（仅内部记录，不返回前端）
     */
    private void handleFailure(String username, String ip, String browser, String os, String reason) {
        loginPolicy.onLoginFailure(username);
        loginLogAppService.save(LoginLog.failure(username, ip, browser, os, reason));
        log.warn("登录失败 username={} ip={} reason={}", username, ip, reason);
    }

    /**
     * 执行风控策略并把领域异常转换为业务异常（携带友好提示）。
     *
     * @param policyAction 策略动作
     * @param message      前端提示
     */
    private void checkPolicy(Runnable policyAction, String message) {
        try {
            policyAction.run();
        } catch (IllegalStateException e) {
            throw new BusinessException(ResultCode.BIZ_ERROR, message);
        }
    }

}
