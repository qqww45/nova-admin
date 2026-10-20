package com.example.admin.interfaces.dto.resp;

import com.example.admin.domain.model.auth.ChallengeTicket;
import com.example.admin.domain.model.auth.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * 人机验证挑战（两阶段登录的中间态响应）。
 *
 * <p>为什么不直接返回"成功/失败"而是给一张票据：
 * 票据把"这次验证"变成服务端可追踪、可限次、可作废的对象，
 * 抗重放、防爆破都建立在它之上；前端只负责原样带回，不参与任何判定。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChallengeResp", description = "人机验证挑战")
public class ChallengeResp {

    /** 一次性票据 ID（验证时需原样回传） */
    @Schema(description = "验证票据ID")
    private String ticketId;

    /** 验证强度：LIGHT（一次按压）/ STRONG（一次性码） */
    @Schema(description = "验证强度", example = "LIGHT")
    private String level;

    /**
     * 当前所处阶段：{@code SLIDER}（先做滑块）/ {@code CODE}（输入一次性码）。
     *
     * <p>为什么需要它：强验证不再是"滑块或码二选一"，而是"先滑块、通过后才发码"。
     * 前端必须知道现在该渲染滑块还是码输入框 —— 只看 level 无法区分，
     * 因为同一张 STRONG 票据在滑块前后处于不同阶段。
     */
    @Schema(description = "当前阶段：SLIDER / CODE", example = "SLIDER")
    private String stage;

    /** 滑块缺口的目标位置（像素）：服务端生成，前端据此渲染，不参与判定下发以外的用途 */
    @Schema(description = "缺口目标x（像素）", example = "128")
    private Integer targetX;

    /** 票据有效期（秒） */
    @Schema(description = "票据有效期（秒）", example = "180")
    private Long expireSeconds;

    /** 面向用户的提示（说明"为什么要验证"，减少困惑） */
    @Schema(description = "提示文案")
    private String hint;

    /**
     * 由领域票据构造响应。
     *
     * @param ticket 票据
     * @return 响应
     */
    /** 阶段：进行人机验证（滑块） */
    public static final String STAGE_SLIDER = "SLIDER";

    /** 阶段：输入一次性验证码 */
    public static final String STAGE_CODE = "CODE";

    /**
     * 由领域票据构造响应。
     *
     * @param ticket 票据
     * @return 响应
     */
    public static ChallengeResp from(ChallengeTicket ticket) {
        long expireSeconds = Math.max(1, Duration.between(java.time.LocalDateTime.now(), ticket.getExpireAt()).toSeconds());
        boolean strong = ticket.getLevel() == RiskLevel.STRONG;
        String stage;
        String hint;
        if (!strong) {
            stage = STAGE_SLIDER;
            hint = "请拖动滑块拼合图片";
        } else if (ticket.isHumanVerified()) {
            // 滑块已通过：此时才允许下发验证码（发码接口会校验这个标记）
            stage = STAGE_CODE;
            hint = "验证已通过，请输入一次性验证码";
        } else {
            stage = STAGE_SLIDER;
            hint = "请先拖动滑块完成验证，之后一次性验证码会发送到您绑定的联系方式";
        }
        return ChallengeResp.builder()
                .ticketId(ticket.getTicketId())
                .level(ticket.getLevel().name())
                .stage(stage)
                .targetX(ticket.getExpectedX())
                .expireSeconds(expireSeconds)
                .hint(hint)
                .build();
    }
}
