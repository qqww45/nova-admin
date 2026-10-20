package com.example.admin.domain.model.auth;

import java.util.List;

/**
 * 滑块拼图的交互样本（值对象）：用户拖动滑块的过程特征。
 *
 * <p>与"按压样本"的区别：滑块存在客观正确的落点（服务端下发的缺口位置），
 * 因此判定分成两层：
 * <ol>
 *     <li><b>位置正确性</b>：最终落点必须落在缺口容差内（硬门槛）；</li>
 *     <li><b>过程合理性</b>：轨迹是否像人拖出来的（评分制）。</li>
 * </ol>
 * 只认位置等于把安全交给"能不能算出缺口坐标"这一件事；
 * 只看轨迹则退化成"能不能模拟出像样的曲线"。两层叠加，攻破成本才不是单点。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public class SlideSample {

    /** 拖动过程中每次采样的 x 坐标（相对拖动起点，单位像素，按时间有序） */
    private final List<Integer> track;

    /** 松手时的最终 x 坐标 */
    private final int finalX;

    /** 服务端下发的缺口目标 x（前端原样回传，用于比对） */
    private final int targetX;

    /** 从按下到松开的总时长（毫秒） */
    private final long durationMs;

    /** 从按下到首次移动的停顿（毫秒）：人通常有反应时间，脚本常常是 0 */
    private final long startDelayMs;

    /** 从挑战下发到提交的总耗时（毫秒） */
    private final long elapsedMs;

    public SlideSample(List<Integer> track, int finalX, int targetX,
                       long durationMs, long startDelayMs, long elapsedMs) {
        this.track = track;
        this.finalX = finalX;
        this.targetX = targetX;
        this.durationMs = durationMs;
        this.startDelayMs = startDelayMs;
        this.elapsedMs = elapsedMs;
    }

    public List<Integer> getTrack() {
        return track;
    }

    public int getFinalX() {
        return finalX;
    }

    public int getTargetX() {
        return targetX;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getStartDelayMs() {
        return startDelayMs;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
