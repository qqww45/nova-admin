package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 完成人机验证的请求。
 *
 * <p>两类证据二选一（由票据等级决定）：
 * <ul>
 *     <li>轻验证：提交滑块落点与拖动轨迹（采样序列、时长、停顿）；</li>
 *     <li>强验证：提交一次性码。</li>
 * </ul>
 *
 * <p>轨迹为什么由前端"原样上报"而不是前端判定：
 * 判定规则一旦放在前端，就等于把考题和答案一起交了出去；
 * 这里前端只是采集器，是否通过完全由服务端决定。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ChallengeVerifyReq", description = "完成人机验证请求")
public class ChallengeVerifyReq {

    /** 一次性票据 ID（登录时由服务端下发） */
    @Schema(description = "验证票据ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "验证票据不能为空")
    private String ticketId;

    /** 用户名（与票据绑定校验） */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 设备标识（与票据绑定校验，必须与发起登录时一致） */
    @Schema(description = "设备标识")
    private String deviceId;

    /** 浏览器指纹（设备标识缺失时用于推导） */
    @Schema(description = "浏览器指纹")
    private String fingerprint;

    /** 滑块最终落点 x（像素），轻验证用 */
    @Schema(description = "滑块落点x（像素）", example = "128")
    private Integer slideX;

    /** 拖动轨迹采样（按时间有序的 x 序列），轻验证用 */
    @Schema(description = "拖动轨迹采样（x 序列）")
    private List<Integer> track;

    /** 从按下到松开的总时长（毫秒），轻验证用 */
    @Schema(description = "拖动时长（毫秒）", example = "900")
    private Long durationMs;

    /** 从按下到首次移动的停顿（毫秒），轻验证用 */
    @Schema(description = "按下到首次移动的停顿（毫秒）", example = "120")
    private Long startDelayMs;

    /** 从挑战下发到提交的总耗时（毫秒），轻验证用 */
    @Schema(description = "总耗时（毫秒）", example = "1500")
    private Long elapsedMs;

    /** 一次性验证码，强验证用 */
    @Schema(description = "一次性验证码", example = "123456")
    private String code;

    /** 是否信任此设备（验证通过后写入信任记录） */
    @Schema(description = "是否信任此设备", example = "true")
    private Boolean trustDevice;
}
