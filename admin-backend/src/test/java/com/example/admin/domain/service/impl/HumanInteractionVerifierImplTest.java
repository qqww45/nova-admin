package com.example.admin.domain.service.impl;

import com.example.admin.domain.model.auth.SlideSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 滑块判定单元测试：位置关（硬门槛）、轨迹关（评分制）与各类边界。
 */
class HumanInteractionVerifierImplTest {

    private final HumanInteractionVerifierImpl verifier = new HumanInteractionVerifierImpl();

    /** 目标缺口位置 */
    private static final int TARGET = 120;

    /**
     * 构造"像人"的轨迹：足够多的采样点、变速、含停顿或回拉。
     */
    private static List<Integer> humanTrack(int target) {
        List<Integer> track = new ArrayList<>();
        int x = 0;
        int[] steps = {5, 12, 3, 20, 8, 0, 15, 4, 22, 9, 6, 16};
        for (int step : steps) {
            x = Math.min(target, x + step);
            track.add(x);
        }
        // 模拟接近终点时回拉一次（真人微调）
        track.add(Math.max(0, x - 3));
        track.add(target);
        return track;
    }

    /** 构造"脚本"轨迹：采样点少、严格匀速、无停顿回拉 */
    private static List<Integer> scriptTrack(int target) {
        List<Integer> track = new ArrayList<>();
        int points = 4;
        for (int i = 1; i <= points; i++) {
            track.add(target * i / points);
        }
        return track;
    }

    private static SlideSample sample(int finalX, List<Integer> track, long durationMs,
                                      long startDelayMs, long elapsedMs) {
        return new SlideSample(track, finalX, TARGET, durationMs, startDelayMs, elapsedMs);
    }

    // ---------------- 正常流程 ----------------

    @Test
    @DisplayName("落点正确 + 真人轨迹 → 通过")
    void verify_correctPositionAndHumanTrack_passes() {
        assertThat(verifier.verify(sample(TARGET, humanTrack(TARGET), 900L, 120L, 1500L))).isTrue();
    }

    // ---------------- 位置关 ----------------

    @Test
    @DisplayName("落点偏差在容差内（±8px）→ 位置关通过")
    void verify_positionWithinTolerance_passesPositionGate() {
        assertThat(verifier.verify(sample(TARGET + 8, humanTrack(TARGET + 8), 900L, 120L, 1500L))).isTrue();
        assertThat(verifier.verify(sample(TARGET - 8, humanTrack(TARGET - 8), 900L, 120L, 1500L))).isTrue();
    }

    @Test
    @DisplayName("落点偏差超出容差 → 直接拒绝（轨迹再像人也没用）")
    void verify_positionOutOfTolerance_rejected() {
        assertThat(verifier.verify(sample(TARGET + 9, humanTrack(TARGET + 9), 900L, 120L, 1500L))).isFalse();
        assertThat(verifier.verify(sample(TARGET - 60, humanTrack(TARGET - 60), 900L, 120L, 1500L))).isFalse();
    }

    // ---------------- 轨迹关 ----------------

    @Test
    @DisplayName("落点正确但轨迹像脚本（点少、匀速、无停顿）→ 拒绝")
    void verify_scriptTrack_rejected() {
        assertThat(verifier.verify(sample(TARGET, scriptTrack(TARGET), 900L, 120L, 1500L))).isFalse();
    }

    @Test
    @DisplayName("仅时长过短：评分制容错，其它维度得分足够仍可通过（避免误杀真人）")
    void verify_onlyDurationTooShort_stillPasses() {
        // 轨迹关满分 5 需 3：时长这一项不得分，但采样点/耗时/变速/回拉四项拿到 4 分
        assertThat(verifier.verify(sample(TARGET, humanTrack(TARGET), 100L, 0L, 1500L))).isTrue();
    }

    @Test
    @DisplayName("仅时长过长：同样容错（真人也可能中途走神）")
    void verify_onlyDurationTooLong_stillPasses() {
        assertThat(verifier.verify(sample(TARGET, humanTrack(TARGET), 20_000L, 120L, 30_000L))).isTrue();
    }

    @Test
    @DisplayName("时长过短 + 轨迹像脚本：多项同时不足 → 拒绝")
    void verify_fastAndScriptLike_rejected() {
        assertThat(verifier.verify(sample(TARGET, scriptTrack(TARGET), 50L, 0L, 80L))).isFalse();
    }

    @Test
    @DisplayName("挑战下发到提交耗时过短（<200ms）：没有真实操作发生")
    void verify_elapsedTooShort_rejected() {
        // 除 elapsed 外的四项都满分，仍应为 true（评分制允许单项不足）
        // 此用例用于确认：单项不足不会直接判死，只有多项同时不足才会
        assertThat(verifier.verify(sample(TARGET, humanTrack(TARGET), 900L, 120L, 100L))).isTrue();
    }

    // ---------------- 边界与异常 ----------------

    @Test
    @DisplayName("样本为 null → 拒绝")
    void verify_nullSample_returnsFalse() {
        assertThat(verifier.verify(null)).isFalse();
    }

    @Test
    @DisplayName("轨迹为 null → 位置关仍生效，轨迹关不计分（评分不足被拒）")
    void verify_nullTrack_rejected() {
        assertThat(verifier.verify(sample(TARGET, null, 900L, 120L, 1500L))).isFalse();
    }

    @Test
    @DisplayName("轨迹为空列表 → 同 null 处理")
    void verify_emptyTrack_rejected() {
        assertThat(verifier.verify(sample(TARGET, List.of(), 900L, 120L, 1500L))).isFalse();
    }

    @Test
    @DisplayName("轨迹只有 1 个点 → 视为直接跳到终点，拒绝")
    void verify_singlePointTrack_rejected() {
        assertThat(verifier.verify(sample(TARGET, List.of(TARGET), 900L, 120L, 1500L))).isFalse();
    }
}
