package com.example.admin.domain.service.impl;

import com.example.admin.domain.model.auth.SlideSample;
import com.example.admin.domain.service.HumanInteractionVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 滑块判定实现：位置正确性（硬门槛）+ 轨迹合理性（评分制）。
 *
 * <p>为什么轨迹用评分而不是硬规则：真人的拖动差异极大（有人一拖到底、有人来回微调、
 * 有人中途停顿），任何单一硬阈值都会误杀正常用户；评分制允许"某项不像人"仍可通过，
 * 而脚本通常会在多个维度同时露馅（瞬时完成、采样稀疏、严格匀速、一眼不停）。
 *
 * <p>五个评分维度：
 * <ul>
 *     <li>采样点数量（过少=直接跳到终点，没有真实拖动过程）；</li>
 *     <li>拖动时长是否落在人类区间（过短=脚本，过长=挂起）；</li>
 *     <li>挑战下发到提交的耗时是否合理（太快=没有真实操作发生）；</li>
 *     <li>速度是否变化（真人不会匀速，匀速直线是脚本的典型特征）；</li>
 *     <li>是否存在停顿或回拉（真人接近终点常会微调）。</li>
 * </ul>
 *
 * <p>阈值与容差集中在此处，便于按线上数据灰度调整。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
public class HumanInteractionVerifierImpl implements HumanInteractionVerifier {

    /** 落点与缺口的允许误差（像素）：太小误杀真人，太大等于放弃位置校验 */
    private static final int POSITION_TOLERANCE = 8;

    /** 轨迹采样点下限 */
    private static final int MIN_TRACK_POINTS = 8;

    /** 拖动时长下限（毫秒） */
    private static final long MIN_DURATION_MS = 300L;

    /** 拖动时长上限（毫秒） */
    private static final long MAX_DURATION_MS = 10_000L;

    /** 挑战下发到提交的总耗时下限（毫秒） */
    private static final long MIN_ELAPSED_MS = 200L;

    /** 速度变异系数阈值：低于此值视为匀速（脚本特征） */
    private static final double SPEED_CV_THRESHOLD = 0.15;

    /** 通过所需的最低分（满分 5） */
    private static final int PASS_SCORE = 3;

    @Override
    public boolean verify(SlideSample sample) {
        if (sample == null) {
            return false;
        }
        // 第一道关：位置正确性。落点不对，轨迹再像人也不通过
        if (Math.abs(sample.getFinalX() - sample.getTargetX()) > POSITION_TOLERANCE) {
            log.debug("滑块落点偏差过大 finalX={} targetX={}", sample.getFinalX(), sample.getTargetX());
            return false;
        }
        // 第二道关：过程合理性
        List<Integer> track = sample.getTrack();
        int score = 0;
        if (track != null && track.size() >= MIN_TRACK_POINTS) {
            score++;
        }
        if (sample.getDurationMs() >= MIN_DURATION_MS && sample.getDurationMs() <= MAX_DURATION_MS) {
            score++;
        }
        if (sample.getElapsedMs() >= MIN_ELAPSED_MS) {
            score++;
        }
        if (isSpeedVarying(track)) {
            score++;
        }
        if (hasPauseOrBacktrack(track)) {
            score++;
        }
        boolean passed = score >= PASS_SCORE;
        log.debug("滑块验证 score={} passed={} points={} durationMs={}",
                score, passed, track == null ? 0 : track.size(), sample.getDurationMs());
        return passed;
    }

    /**
     * 速度是否变化：用相邻位移的变异系数（标准差 / 均值）衡量。
     *
     * @param track 轨迹采样
     * @return true 表示速度有变化（更像人）
     */
    private boolean isSpeedVarying(List<Integer> track) {
        if (track == null || track.size() < 3) {
            return false;
        }
        double[] steps = new double[track.size() - 1];
        double sum = 0;
        for (int i = 1; i < track.size(); i++) {
            steps[i - 1] = Math.abs(track.get(i) - track.get(i - 1));
            sum += steps[i - 1];
        }
        double mean = sum / steps.length;
        if (mean <= 0) {
            return false;
        }
        double variance = 0;
        for (double step : steps) {
            variance += (step - mean) * (step - mean);
        }
        double std = Math.sqrt(variance / steps.length);
        return std / mean > SPEED_CV_THRESHOLD;
    }

    /**
     * 是否存在停顿或回拉：真人接近终点通常会减速、微调甚至往回拉一点。
     *
     * @param track 轨迹采样
     * @return true 表示存在停顿或回拉
     */
    private boolean hasPauseOrBacktrack(List<Integer> track) {
        if (track == null || track.size() < 3) {
            return false;
        }
        for (int i = 1; i < track.size(); i++) {
            int delta = track.get(i) - track.get(i - 1);
            // delta == 0 表示停顿采样，delta < 0 表示往回拉
            if (delta <= 0) {
                return true;
            }
        }
        return false;
    }
}
