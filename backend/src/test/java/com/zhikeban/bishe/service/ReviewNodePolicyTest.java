package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.ReviewTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `ReviewNodePolicy` 单测（`ENG-014`；`§ENG-014` `R1`／`R2`／`R3` 的行为面）。
 *
 * <p>★ 断言点：节点集合恰为 `1/2/4/7/15/30`、`plan_date = 基准日 + 节点`、`stage` 落**节点天数**、
 * 「仍需巩固」重置从**今天**重排一轮、空节点回退默认值。
 */
class ReviewNodePolicyTest {

    private static final LocalDate BASE = LocalDate.of(2026, 10, 10);

    private final ReviewNodePolicy policy = new ReviewNodePolicy();

    @Test
    void defaultNodesAreTheSixEbbinghausStages() {
        assertThat(ReviewNodePolicy.DEFAULT_NODES).containsExactly(1, 2, 4, 7, 15, 30);
        assertThat(policy.plan(BASE, 5L, 7L, ReviewNodePolicy.DEFAULT_NODES)).hasSize(6);
    }

    @Test
    void planDateIsBasePlusNodeAndStageIsTheNodeItself() {
        List<ReviewTask> tasks = policy.plan(BASE, 5L, 7L, ReviewNodePolicy.DEFAULT_NODES);

        assertThat(tasks).extracting(ReviewTask::getStage).containsExactly(1, 2, 4, 7, 15, 30);
        assertThat(tasks).extracting(ReviewTask::getPlanDate).containsExactly(
                BASE.plusDays(1), BASE.plusDays(2), BASE.plusDays(4),
                BASE.plusDays(7), BASE.plusDays(15), BASE.plusDays(30));
        assertThat(tasks).allSatisfy(t -> {
            assertThat(t.getWrongId()).isEqualTo(5L);
            assertThat(t.getUserId()).isEqualTo(7L);
            assertThat(t.getStatus()).isZero();
            assertThat(t.getPlanDate()).isEqualTo(BASE.plusDays(t.getStage()));
        });
    }

    @Test
    void resetReplansFromTodayNotFromTheOriginalEntryDate() {
        LocalDate today = LocalDate.of(2026, 11, 1);

        List<ReviewTask> replanned = policy.reset(today, 5L, 7L);

        assertThat(replanned).hasSize(6);
        assertThat(replanned).extracting(ReviewTask::getStage).containsExactly(1, 2, 4, 7, 15, 30);
        assertThat(replanned).extracting(ReviewTask::getPlanDate).containsExactly(
                today.plusDays(1), today.plusDays(2), today.plusDays(4),
                today.plusDays(7), today.plusDays(15), today.plusDays(30));
    }

    @Test
    void emptyOrNullNodesFallBackToTheDefaultSequence() {
        assertThat(policy.plan(BASE, 5L, 7L, null)).hasSize(6);
        assertThat(policy.plan(BASE, 5L, 7L, List.of())).hasSize(6);
    }

    @Test
    void planWithoutABaseDateIsRejected() {
        assertThatThrownBy(() -> policy.plan(null, 5L, 7L, ReviewNodePolicy.DEFAULT_NODES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
