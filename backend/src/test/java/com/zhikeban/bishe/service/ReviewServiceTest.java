package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.mapper.ReviewTaskMapper;
import com.zhikeban.bishe.support.MybatisLambdaCache;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * `ReviewService` 单测（`ENG-014`；`§ENG-014` `R3`／`R4`／`R5`／`P4` 的行为面）。
 *
 * <p>★ 覆盖点：**今日复习只读**、**「仍需巩固」重置周期**、**跳过不重置**、
 * **完成/跳过的条件更新与 409**（二次提交不重复计分）、**按 userId 过滤**（他人任务当作不存在）。
 */
class ReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final long USER = 7L;
    private static final long TASK_ID = 11L;
    private static final long WRONG_ID = 5L;

    private ReviewTaskMapper reviewTaskMapper;
    private ReviewNodePolicy reviewNodePolicy;
    private MasteryService masteryService;
    private ReviewService service;

    @BeforeAll
    static void warmUpLambdaCache() {
        MybatisLambdaCache.warmUp();
    }

    @BeforeEach
    void setUp() {
        reviewTaskMapper = mock(ReviewTaskMapper.class);
        reviewNodePolicy = mock(ReviewNodePolicy.class);
        masteryService = mock(MasteryService.class);
        service = new ReviewService(reviewTaskMapper, reviewNodePolicy, masteryService, CLOCK);
    }

    private static ReviewTask task(long id, long wrongId, long userId, int status) {
        ReviewTask t = new ReviewTask();
        t.setId(id);
        t.setWrongId(wrongId);
        t.setUserId(userId);
        t.setPlanDate(TODAY.minusDays(1));
        t.setStage(1);
        t.setStatus(status);
        return t;
    }

    private static List<ReviewTask> replannedTasks() {
        List<ReviewTask> tasks = new ArrayList<>();
        for (int stage : ReviewNodePolicy.DEFAULT_NODES) {
            ReviewTask t = new ReviewTask();
            t.setWrongId(WRONG_ID);
            t.setUserId(USER);
            t.setPlanDate(TODAY.plusDays(stage));
            t.setStage(stage);
            t.setStatus(0);
            tasks.add(t);
        }
        return tasks;
    }

    /* ───────── 今日复习（只读）───────── */

    @Test
    void todayOnlyReadsAndNeverWritesAnything() {
        List<ReviewTask> due = List.of(task(11L, WRONG_ID, USER, 0), task(12L, 6L, USER, 0));
        when(reviewTaskMapper.selectList(any())).thenReturn(due);

        List<ReviewTask> result = service.today(USER);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(due);
        verify(reviewTaskMapper, never()).insert(any(ReviewTask.class));
        verify(reviewTaskMapper, never()).update(any(), any());
        verifyNoInteractions(reviewNodePolicy);
    }

    /* ───────── 完成（API-41）───────── */

    @Test
    void completeUsesAConditionalUpdateAndRecalculatesMastery() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(task(TASK_ID, WRONG_ID, USER, 0));
        when(reviewTaskMapper.update(isNull(), any())).thenReturn(1);

        service.complete(TASK_ID, USER, 2);

        verify(reviewTaskMapper).update(isNull(), any());
        verify(masteryService).recalcForWrong(WRONG_ID);
        verify(reviewNodePolicy, never()).reset(any(), any(), any());
        verify(reviewTaskMapper, never()).insert(any(ReviewTask.class));
    }

    @Test
    void completeWithAgainResultResetsTheWholePlanFromToday() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(task(TASK_ID, WRONG_ID, USER, 0));
        when(reviewTaskMapper.update(isNull(), any())).thenReturn(1);
        when(reviewNodePolicy.reset(TODAY, WRONG_ID, USER)).thenReturn(replannedTasks());

        service.complete(TASK_ID, USER, 0);

        verify(reviewNodePolicy).reset(TODAY, WRONG_ID, USER);
        verify(reviewTaskMapper, times(6)).insert(any(ReviewTask.class));
        // 两次 update：① 完成本条（0→1）② 关闭该题其余未完成任务（0→2）
        verify(reviewTaskMapper, times(2)).update(isNull(), any());
        verify(masteryService).recalcForWrong(WRONG_ID);
    }

    @Test
    void completeWithMasteredResultDoesNotResetThePlan() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(task(TASK_ID, WRONG_ID, USER, 0));
        when(reviewTaskMapper.update(isNull(), any())).thenReturn(1);

        service.complete(TASK_ID, USER, 2);

        verify(reviewNodePolicy, never()).reset(any(), any(), any());
        verify(reviewTaskMapper, never()).insert(any(ReviewTask.class));
    }

    @Test
    void secondSubmissionGets409AndDoesNotScoreTwice() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(task(TASK_ID, WRONG_ID, USER, 1));
        when(reviewTaskMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.complete(TASK_ID, USER, 2))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        verify(masteryService, never()).recalcForWrong(any());
    }

    @Test
    void invalidResultIsRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> service.complete(TASK_ID, USER, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.complete(TASK_ID, USER, -1))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.complete(TASK_ID, USER, 3))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        verify(reviewTaskMapper, never()).selectOne(any());
    }

    @Test
    void completingSomeoneElsesTaskLooksLikeNotFound() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.complete(TASK_ID, USER, 2))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        verify(reviewTaskMapper, never()).update(any(), any());
    }

    /* ───────── 跳过（不重置）───────── */

    @Test
    void skipMarksTheTaskSkippedWithoutResettingThePlan() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(task(TASK_ID, WRONG_ID, USER, 0));
        when(reviewTaskMapper.update(isNull(), any())).thenReturn(1);

        service.skip(TASK_ID, USER);

        verify(reviewTaskMapper).update(isNull(), any());
        verify(reviewNodePolicy, never()).reset(any(), any(), any());
        verify(reviewTaskMapper, never()).insert(any(ReviewTask.class));
        verify(masteryService, never()).recalcForWrong(any());
    }

    @Test
    void skippingAnAlreadyHandledTaskConflicts() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(task(TASK_ID, WRONG_ID, USER, 1));
        when(reviewTaskMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.skip(TASK_ID, USER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void skippingSomeoneElsesTaskLooksLikeNotFound() {
        when(reviewTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.skip(TASK_ID, USER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        verify(reviewTaskMapper, never()).update(any(), any());
    }
}
