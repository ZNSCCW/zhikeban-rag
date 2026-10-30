package com.zhikeban.bishe.service;

import java.time.LocalDate;

/**
 * 掌握度计算输入（纯数据；`§ENG-014` `M` 组）。
 *
 * @param masteredCount   已掌握次数（`review_task.result = 2` 的次数）
 * @param againCount      仍需巩固次数（`review_task.result = 0` 的次数）
 * @param totalReviews    累计复习次数（`result` 非空的条数）
 * @param lastReviewedDate 最近一次复习日期；**从未复习时传 {@code null}**
 * @param createdAt       错题入库日期；用于"从未复习 ⇒ 基准回退入库时间"（`M2`）
 */
public record MasteryInput(int masteredCount,
                           int againCount,
                           int totalReviews,
                           LocalDate lastReviewedDate,
                           LocalDate createdAt) {
}
