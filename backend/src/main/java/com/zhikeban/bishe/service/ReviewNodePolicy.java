package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.ReviewTask;

import java.time.LocalDate;
import java.util.List;

/**
 * 复习节点策略（详设 §4.4.3；`§ENG-014` `R` 组）。
 *
 * <p>★ 默认复习节点 = **1、2、4、7、15、30 天**（艾宾浩斯遗忘曲线的近似节点；`R1` 要求"恰 6 个"）。
 *
 * <p>★ 计划生成口径（`R2`）：{@code plan_date = 基准日 + 节点天数}，且 {@code stage} **直接落节点天数**
 * —— 两者在同一处（{@code plusDays(d)} 与 {@code setStage(d)}）完成，避免"日期按节点、stage 按序号"这类分叉。
 *
 * <p>★ 「仍需巩固」重置（`R3`）：{@code reset(today, ...)} = 按今天重排一轮（与 `plan` 同一份实现）。
 */
public class ReviewNodePolicy {

    /** 默认复习节点（天）：1、2、4、7、15、30 —— 与 `SRS` `FR-5-07` 逐字一致。 */
    public static final List<Integer> DEFAULT_NODES = List.of(1, 2, 4, 7, 15, 30);

    /**
     * 生成一轮复习任务（错题入库 / 「仍需巩固」重置共用）。
     *
     * @param baseDate 基准日（入库日或"今天"）
     * @param wrongId  错题标识
     * @param userId   所属学生
     * @param nodes    节点天数序列；为空时回退到 {@link #DEFAULT_NODES}
     * @return 每个节点一条 {@link ReviewTask}（状态置 0 待复习）
     */
    public List<ReviewTask> plan(LocalDate baseDate, Long wrongId, Long userId, List<Integer> nodes) {
        if (baseDate == null) {
            throw new IllegalArgumentException("复习计划必须有基准日");
        }
        List<Integer> effective = (nodes == null || nodes.isEmpty()) ? DEFAULT_NODES : nodes;
        return effective.stream().map(d -> {
            ReviewTask task = new ReviewTask();
            task.setWrongId(wrongId);
            task.setUserId(userId);
            task.setPlanDate(baseDate.plusDays(d));
            task.setStage(d);
            task.setStatus(0);
            return task;
        }).toList();
    }

    /**
     * 「仍需巩固」⇒ 重置该题复习周期：**从今天起重排一轮**（详设 §4.4.3 的 `reset`）。
     *
     * @param today   今天（由调用方注入）
     * @param wrongId 错题标识
     * @param userId  所属学生
     * @return 新的一轮复习任务
     */
    public List<ReviewTask> reset(LocalDate today, Long wrongId, Long userId) {
        return plan(today, wrongId, userId, DEFAULT_NODES);
    }
}
