package com.zhikeban.bishe.service;

/**
 * 掌握度模型参数（`SRS §3.5` 创新点 3 ／ 详设 §4.4.1）。
 *
 * <p>公式：{@code M = clamp(100 × (w1·C + w2·R + w3·T), 0, 100)}，其中
 * {@code C = 已掌握 / (已掌握 + 仍需巩固 + 1)}、{@code R = e^(−λ·Δt)}、{@code T = min(1, 累计复习 / 饱和阈值)}。
 *
 * <p>★ 默认取值**逐字**按详设 §4.4.1 的配置表：`w1=0.5`、`w2=0.3`、`w3=0.2`、`λ=0.05`、饱和阈值 `5`
 * （`§ENG-014` `M1`）。权重之和必须为 1（详设 §4.4.6：保存配置时拒绝，错误码 `40003`）。
 *
 * <p>★ 参数本身是**不可变值对象**：后续接 `system_config`（`TB-26`）热刷新时只需替换实例，
 * 计算公式与调用方都不动（详设 §4.4.5）。
 *
 * @param w1               正确性权重（默认 0.5）
 * @param w2               复习时效权重（默认 0.3）
 * @param w3               熟练度权重（默认 0.2）
 * @param lambda           时间衰减系数 λ（默认 0.05）
 * @param familiarThreshold 熟练饱和阈值（默认 5）
 */
public record MasteryParams(double w1, double w2, double w3, double lambda, int familiarThreshold) {

    /** 详设 §4.4.1 的默认参数（权重 0.5/0.3/0.2、λ=0.05、饱和阈值 5）。 */
    public static final MasteryParams DEFAULT = new MasteryParams(0.5, 0.3, 0.2, 0.05, 5);

    /** 参数自检：权重之和必须为 1（详设 §4.4.6），衰减系数必须在 (0, 1]，阈值必须为正整数。 */
    public MasteryParams {
        if (Math.abs(w1 + w2 + w3 - 1.0) > 1e-9) {
            throw new IllegalArgumentException("掌握度权重之和必须为 1：w1+w2+w3=" + (w1 + w2 + w3));
        }
        if (!(lambda > 0.0) || lambda > 1.0) {
            throw new IllegalArgumentException("时间衰减系数 λ 必须满足 0 < λ ≤ 1：" + lambda);
        }
        if (familiarThreshold <= 0) {
            throw new IllegalArgumentException("熟练饱和阈值必须是正整数：" + familiarThreshold);
        }
    }
}
