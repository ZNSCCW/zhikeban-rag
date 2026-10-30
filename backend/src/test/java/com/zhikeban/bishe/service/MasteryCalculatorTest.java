package com.zhikeban.bishe.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `MasteryCalculator` 单测（`ENG-014`；`§ENG-014` `M` 组 `B3` 的"真在测行为"）。
 *
 * <p>★ **金标值**（`T4`：`76.00 / 60.90 / 53.40 / 49.01`）不是抄来的，是按详设 §4.4.1 的公式
 * 在固定输入下独立复算出来的：
 *
 * <pre>
 *   输入 mastered=3, again=1, reviews=4  ⇒ C=3/5=0.6、T=4/5=0.8
 *   M = 100 × (0.5×0.6 + 0.3×e^(−0.05·Δt) + 0.2×0.8) = 46 + 30·e^(−0.05·Δt)
 *   Δt=0 → 76.00 ｜ Δt=14 → 60.90 ｜ Δt=28 → 53.40 ｜ Δt=46 → 49.01
 * </pre>
 *
 * <p>★ 四个值对参数/衰减都**有分辨力**：λ 若写成 0.5 ⇒ Δt=14 得 `46.03`；无衰减 ⇒ 得 `76.00`；
 * 权重 w1=0.4 ⇒ Δt=0 得 `70.00`（都不是金标值）。
 */
class MasteryCalculatorTest {

    private static final LocalDate BASE = LocalDate.of(2026, 10, 10);

    private final MasteryCalculator calculator = new MasteryCalculator();

    private static MasteryInput input(int mastered, int again, int reviews, LocalDate reviewedAt) {
        return new MasteryInput(mastered, again, reviews, reviewedAt, LocalDate.of(2026, 1, 1));
    }

    private BigDecimal calcAt(MasteryInput in, LocalDate base) {
        return calculator.calc(in, MasteryParams.DEFAULT, base);
    }

    @Test
    void goldenValuesFollowTheDocumentedFormula() {
        MasteryInput in = input(3, 1, 4, BASE);

        assertThat(calcAt(in, BASE)).isEqualByComparingTo("76.00");
        assertThat(calcAt(in, BASE.plusDays(14))).isEqualByComparingTo("60.90");
        assertThat(calcAt(in, BASE.plusDays(28))).isEqualByComparingTo("53.40");
        assertThat(calcAt(in, BASE.plusDays(46))).isEqualByComparingTo("49.01");
    }

    @Test
    void decayIsExpressedToTwoDecimalsWithHalfUp() {
        BigDecimal fourteen = calcAt(input(3, 1, 4, BASE), BASE.plusDays(14));

        // 30·e^(−0.7) = 14.8977… ⇒ 46 + 14.8977 = 60.8977… ⇒ HALF_UP 保留两位 = 60.90
        assertThat(fourteen).isEqualByComparingTo(new BigDecimal("60.90"));
        assertThat(fourteen.scale()).isEqualTo(2);
    }

    @Test
    void negativeDeltaIsClampedToZeroInsteadOfRewardingTheFuture() {
        // 客户端时间倒挂（Δt = −3）⇒ max(0, Δt) ⇒ 与 Δt=0 同值，绝不因"未来复习"加分
        assertThat(calcAt(input(3, 1, 4, BASE), BASE.minusDays(3))).isEqualByComparingTo("76.00");
        assertThat(calcAt(input(3, 1, 4, BASE), BASE.minusDays(3)))
                .isEqualByComparingTo(calcAt(input(3, 1, 4, BASE), BASE));
    }

    @Test
    void neverReviewedFallsBackToTheCreatedAtBaseline() {
        // 从未复习（lastReviewedDate=null）⇒ 基准取入库时间（30 天前）
        MasteryInput never = new MasteryInput(3, 1, 4, null, BASE.minusDays(14));

        assertThat(calcAt(never, BASE)).isEqualByComparingTo("60.90");
        assertThat(calcAt(never, BASE)).isEqualByComparingTo(calcAt(input(3, 1, 4, BASE), BASE.plusDays(14)));
    }

    @Test
    void familiaritySaturatesAtTheThreshold() {
        // reviews=10 ⇒ T=min(1, 10/5)=1 ⇒ 100×(0.3+0.3×1+0.2)=80.00
        assertThat(calcAt(input(3, 1, 10, BASE), BASE)).isEqualByComparingTo("80.00");
    }

    @Test
    void levelBoundariesAreFortySeventyNinety() {
        assertThat(calculator.level(0.0)).isZero();
        assertThat(calculator.level(39.99)).isZero();
        assertThat(calculator.level(40.0)).isEqualTo(1);
        assertThat(calculator.level(69.99)).isEqualTo(1);
        assertThat(calculator.level(70.0)).isEqualTo(2);
        assertThat(calculator.level(89.99)).isEqualTo(2);
        assertThat(calculator.level(90.0)).isEqualTo(3);
        assertThat(calculator.level(100.0)).isEqualTo(3);
        assertThat(calculator.level((BigDecimal) null)).isZero();
    }

    @Test
    void defaultParamsMatchTheDesignTable() {
        MasteryParams p = MasteryParams.DEFAULT;

        assertThat(p.w1()).isEqualTo(0.5);
        assertThat(p.w2()).isEqualTo(0.3);
        assertThat(p.w3()).isEqualTo(0.2);
        assertThat(p.lambda()).isEqualTo(0.05);
        assertThat(p.familiarThreshold()).isEqualTo(5);
    }

    @Test
    void paramsRejectIllegalWeightsLambdaAndThreshold() {
        assertThatThrownBy(() -> new MasteryParams(0.4, 0.3, 0.2, 0.05, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权重之和");
        assertThatThrownBy(() -> new MasteryParams(0.5, 0.3, 0.2, 0.0, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("λ");
        assertThatThrownBy(() -> new MasteryParams(0.5, 0.3, 0.2, 0.05, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("阈值");
    }

    @Test
    void calcRequiresAllThreeArguments() {
        assertThatThrownBy(() -> calculator.calc(null, MasteryParams.DEFAULT, BASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calc(input(3, 1, 4, BASE), null, BASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calc(input(3, 1, 4, BASE), MasteryParams.DEFAULT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreIsAlwaysInsideTheZeroToHundredRange() {
        assertThat(calcAt(input(0, 0, 0, null), BASE)).isEqualByComparingTo("0.00");
        assertThat(calcAt(input(0, 0, 0, null), BASE)).isGreaterThanOrEqualTo(new BigDecimal("0.00"));
        assertThat(calcAt(input(999, 0, 999, BASE), BASE)).isLessThanOrEqualTo(new BigDecimal("100.00"));
    }
}
