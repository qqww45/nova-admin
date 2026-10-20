package com.example.admin.domain.model.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 人机验证票据（值对象）：一次验证挑战的服务端凭证。
 *
 * <p>一次性票据是整个方案抗重放的基础。它必须同时具备：
 * <ul>
 *     <li><b>唯一</b>：每次挑战都是新票据，用过即焚；</li>
 *     <li><b>绑定</b>：与账号、设备、来源网段绑定，换个地方用立即失效；</li>
 *     <li><b>短时效</b>：过期作废，压缩可攻击窗口；</li>
 *     <li><b>不可篡改</b>：票据由服务端签发与校验，前端只负责原样带回。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class ChallengeTicket {

    /** 票据 ID */
    private final String ticketId;

    /** 用户名（绑定账号） */
    private final String username;

    /** 设备标识（绑定设备） */
    private final String deviceId;

    /** 来源网段（绑定网络环境，避免票据被搬到别的网络使用） */
    private final String ipSegment;

    /** 滑块缺口的目标位置（像素）：由服务端生成并绑定票据，前端只负责渲染与回传 */
    private final int expectedX;

    /** 要求的验证强度 */
    private final RiskLevel level;

    /** 签发时间 */
    private final LocalDateTime issuedAt;

    /** 过期时间 */
    private final LocalDateTime expireAt;

    /** 强验证用的一次性码（仅 STRONG 等级生成） */
    private String oneTimeCode;

    /** 一次性码已尝试次数（超过上限即作废，防止码被爆破） */
    private int codeAttempts;

    /**
     * 人机验证（滑块）是否已通过。
     *
     * <p>为什么 STRONG 票据也要先过滑块：下发邮件/短信验证码是一个<b>有外部副作用</b>的操作
     * —— 它会产生真实邮件，可以被用来骚扰指定用户（邮件轰炸），也会消耗通道配额。
     * 如果只要拿到票据就能触发发码，那么"弱口令 + 脚本循环"就能把目标用户的邮箱打满，
     * 而整条链路上没有任何人机门槛。滑块把"自动化"挡在发码之前。
     *
     * <p>无障碍降级（用户确实无法拖动滑块）走 {@link #sendWithoutHumanVerify} 记录的另一条限流口径，
     * 仍然可用，但额度更紧。
     */
    private boolean humanVerified;

    /** 已下发验证码次数（防轰炸） */
    private int codeSendCount;

    /** 未通过人机验证（降级通道）已下发验证码次数 */
    private int sendWithoutHumanVerify;

    /** 最近一次下发时间（用于冷却判断） */
    private LocalDateTime lastCodeSentAt;

    /**
     * 构造票据。
     *
     * <p>{@code @JsonCreator} 的用途：Redis 实现把票据序列化为 JSON 存储，
     * 反序列化时需要一个能接收全部字段的入口（本类字段均为 final，没有无参构造 + setter 可用）。
     * 这里的注解只服务于持久化，不改变任何业务语义。
     */
    @JsonCreator
    public ChallengeTicket(@JsonProperty("ticketId") String ticketId,
                           @JsonProperty("username") String username,
                           @JsonProperty("deviceId") String deviceId,
                           @JsonProperty("ipSegment") String ipSegment,
                           @JsonProperty("expectedX") int expectedX,
                           @JsonProperty("level") RiskLevel level,
                           @JsonProperty("issuedAt") LocalDateTime issuedAt,
                           @JsonProperty("expireAt") LocalDateTime expireAt) {
        this.ticketId = ticketId;
        this.username = username;
        this.deviceId = deviceId;
        this.ipSegment = ipSegment;
        this.expectedX = expectedX;
        this.level = level;
        this.issuedAt = issuedAt;
        this.expireAt = expireAt;
    }

    /**
     * 判断是否已过期。
     *
     * @return true 表示已过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expireAt);
    }

    /**
     * 判断是否与给定上下文匹配（账号 + 设备 + 网段三者全等才算有效）。
     *
     * @param username   用户名
     * @param deviceId   设备标识
     * @param ipSegment  来源网段
     * @return true 表示匹配
     */
    public boolean matches(String username, String deviceId, String ipSegment) {
        return this.username.equals(username)
                && this.deviceId.equals(deviceId)
                && this.ipSegment.equals(ipSegment);
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getUsername() {
        return username;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getIpSegment() {
        return ipSegment;
    }

    public int getExpectedX() {
        return expectedX;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public String getOneTimeCode() {
        return oneTimeCode;
    }

    public void setOneTimeCode(String oneTimeCode) {
        this.oneTimeCode = oneTimeCode;
    }

    public int getCodeAttempts() {
        return codeAttempts;
    }

    public void increaseCodeAttempts() {
        this.codeAttempts++;
    }

    /**
     * 判断一次性码尝试次数是否已用完。
     *
     * @param maxAttempts 上限
     * @return true 表示已用完，票据应作废
     */
    public boolean isCodeAttemptsExhausted(int maxAttempts) {
        return codeAttempts >= maxAttempts;
    }

    public boolean isHumanVerified() {
        return humanVerified;
    }

    /**
     * 标记人机验证已通过。
     *
     * <p>注意：调用方必须随后 {@code save} 回写存储 —— 存储返回的是快照，改了不会自动持久化。
     */
    public void markHumanVerified() {
        this.humanVerified = true;
    }

    public int getCodeSendCount() {
        return codeSendCount;
    }

    public int getSendWithoutHumanVerify() {
        return sendWithoutHumanVerify;
    }

    public LocalDateTime getLastCodeSentAt() {
        return lastCodeSentAt;
    }

    /**
     * 记录一次验证码下发。
     *
     * @param viaHumanVerify 是否经过了人机验证（false 表示走无障碍降级通道）
     */
    public void noteCodeSent(boolean viaHumanVerify) {
        this.codeSendCount++;
        if (!viaHumanVerify) {
            this.sendWithoutHumanVerify++;
        }
        this.lastCodeSentAt = LocalDateTime.now();
    }

    /**
     * 判断当前是否可以下发验证码（冷却 + 额度）。
     *
     * @param coolDownSeconds 两次下发之间的最小间隔
     * @param maxCount        单票据最多下发次数
     * @return true 表示允许下发
     */
    public boolean canSendCode(long coolDownSeconds, int maxCount) {
        if (codeSendCount >= maxCount) {
            return false;
        }
        if (lastCodeSentAt == null) {
            return true;
        }
        return Duration.between(lastCodeSentAt, LocalDateTime.now()).toSeconds() >= coolDownSeconds;
    }

    /**
     * 距下次可下发还需等待的秒数（用于给用户明确的提示文案）。
     *
     * @param coolDownSeconds 冷却秒数
     * @return 还需等待的秒数
     */
    public long nextSendWaitSeconds(long coolDownSeconds) {
        if (lastCodeSentAt == null) {
            return 0;
        }
        long elapsed = Duration.between(lastCodeSentAt, LocalDateTime.now()).toSeconds();
        return Math.max(0, coolDownSeconds - elapsed);
    }
}
