package com.zhikeban.bishe.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 掌握度计算的**无状态纯函数**（详设 §4.4.1；`§ENG-014` `M` 组）。
 *
 * <p>★ 纯函数的两个硬要求（`M6`／`A-5`）：
 * ① **基准时刻只能由参数注入**（{@link #calc(MasteryInput, MasteryParams, LocalDate)} 的第三个参数），
 *    函数体内**不得**读系统时间（没有 {@code LocalDate.now()}／{@code LocalDateTime.now()}／{@code Instant.now()}）；
 * ② **无副作用、无框架依赖**，便于单测与"每日 0 点批量重算"复用同一份实现。
 *
 * <p>★ 衰减口径（`M2`）：{@code Δt} 只取**整天**（{@code ChronoUnit.DAYS.between}），负值（客户端时间倒挂）
 * 取 {@code max(0, Δt)}；**从未复习**（{@code lastReviewedDate == null}）时基准回退到**错题入库时间**
 * （详设 §4.4.2），避免 {@code Δt} 无定义。
 *
 * <p>★ 舍入口径（`M3`）：结果一律 {@code setScale(2, RoundingMode.HALF_UP)}，与库上
 * {@code DECIMAL(5,2)} 的写入口径一致（详设 §4.4.6「写库前再按 DECIMAL(5,2) 四舍五入」）。
 */
public class MasteryCalculator {

    /** 分值区间上限（`wrong_book.mastery_score` 库上 `CHECK 0 ~ 100`）。 */
    private static final double MAX_SCORE = 100.0;

    /**
     * 计算单题掌握度。
     *
     * @param in       输入（计数 / 最近复习日 / 入库日）
     * @param p        参数（默认见 {@link MasteryParams#DEFAULT}）
     * @param baseDate **基准时刻**（"今天"由调用方注入 ⇒ 单测与批量重算都幂等）
     * @return 0 ~ 100 的两位小数分值（`HALF_UP`）
     */
    public BigDecimal calc(MasteryInput in, MasteryParams p, LocalDate baseDate) {
        if (in == null || p == null || baseDate == null) {
            throw new IllegalArgumentException("掌握度计算需要输入、参数与基准时刻三者齐备");
        }
        int mastered = Math.max(0, in.masteredCount());
        int again = Math.max(0, in.againCount());
        int reviews = Math.max(0, in.totalReviews());

        double c = mastered * 1.0 / (mastered + again + 1.0);
        LocalDate base = in.lastReviewedDate() != null ? in.lastReviewedDate() : in.createdAt();
        long deltaDays = base == null ? 0L : ChronoUnit.DAYS.between(base, baseDate);
        double r = Math.exp(-p.lambda() * Math.max(0, deltaDays));
        double t = Math.min(1.0, reviews * 1.0 / p.familiarThreshold());
        double raw = MAX_SCORE * (p.w1() * c + p.w2() * r + p.w3() * t);
        double clamped = Math.max(0.0, Math.min(MAX_SCORE, raw));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 等级换算（详设 §4.4.1 的等级表；库上 `CHECK 0 ~ 3`）。
     *
     * <p>{@code >=90 → 3}、{@code >=70 → 2}、{@code >=40 → 1}，否则 0。
     *
     * @param score 掌握度分值（0 ~ 100）
     * @return 0 未掌握 / 1 模糊 / 2 基本掌握 / 3 已掌握
     */
    public int level(double score) {
        if (score >= 90) {
            return 3;
        }
        if (score >= 70) {
            return 2;
        }
        if (score >= 40) {
            return 1;
        }
        return 0;
    }

    /** 等级换算（{@link BigDecimal} 版，写库时直接用）。 */
    public int level(BigDecimal score) {
        return score == null ? 0 : level(score.doubleValue());
    }
}
