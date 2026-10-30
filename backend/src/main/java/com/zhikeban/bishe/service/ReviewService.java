package com.zhikeban.bishe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.mapper.ReviewTaskMapper;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 复习服务（`SRS` `FR-5-07`／`FR-5-09`；`API-40`／`API-41`；详设 §4.4.3）。
 *
 * <p>★ 三条语义口径（`§ENG-014` `R` 组）：
 * <ul>
 *   <li><b>今日复习只读</b>（{@code R4}）：{@code plan_date <= today} 且 {@code status = 0}；
 *       本方法**不生成**新任务（逾期项不高亮成新任务，见 `SRS` UC-5-07 备选 A1）。</li>
 *   <li><b>仍需巩固 ⇒ 重置</b>（{@code R3}）：{@code result = 0} 时关闭该题未完成任务并按今天重排一轮。</li>
 *   <li><b>跳过 ⇒ 不重置</b>（{@code R5}）：{@code status = 2}，**不动**周期。</li>
 * </ul>
 *
 * <p>★ **幂等/并发**（{@code P4}）：完成与跳过都用**条件更新**（`WHERE status = 0`），
 * 影响 0 行 ⇒ `409`（已完成的复习项不重复计分，详设 §10.2）。
 */
@Service
public class ReviewService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewNodePolicy reviewNodePolicy;
    private final MasteryService masteryService;
    private final Clock clock;

    public ReviewService(ReviewTaskMapper reviewTaskMapper, ReviewNodePolicy reviewNodePolicy,
                         MasteryService masteryService, Clock clock) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewNodePolicy = reviewNodePolicy;
        this.masteryService = masteryService;
        this.clock = clock;
    }

    /**
     * 今日复习任务（`API-40`）—— **只读**：查到期的未完成任务，不写任何一行。
     *
     * @param userId 当前登录学生
     * @return 到期待复习任务（按计划日、id 升序）
     */
    public List<ReviewTask> today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return reviewTaskMapper.selectList(Wrappers.<ReviewTask>lambdaQuery()
                .eq(ReviewTask::getUserId, userId)
                .le(ReviewTask::getPlanDate, today)
                .eq(ReviewTask::getStatus, 0)
                .orderByAsc(ReviewTask::getPlanDate)
                .orderByAsc(ReviewTask::getId));
    }

    /**
     * 提交复习结果（`API-41`）。
     *
     * <p>★ 条件更新 `status: 0 → 1` 与 `result`/`reviewed_at` 同一次写；影响 0 行 ⇒ `409`
     * （`P4`：二次提交不重复计分）。
     *
     * @param taskId 复习任务标识
     * @param userId 当前登录学生
     * @param result 0 仍不会 / 1 模糊 / 2 已掌握
     */
    @Transactional
    public void complete(Long taskId, Long userId, Integer result) {
        if (result == null || result < 0 || result > 2) {
            throw ApiException.badRequest("复习结果只能是 0（仍不会）/1（模糊）/2（已掌握）");
        }
        ReviewTask task = requireOwnTask(taskId, userId);
        int rows = reviewTaskMapper.update(null, Wrappers.<ReviewTask>lambdaUpdate()
                .eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getStatus, 0)
                .set(ReviewTask::getStatus, 1)
                .set(ReviewTask::getResult, result)
                .set(ReviewTask::getReviewedAt, LocalDateTime.now(clock)));
        if (rows == 0) {
            throw ApiException.conflict("该复习任务已完成或已跳过，不能重复提交（taskId=" + taskId + "）");
        }

        if (result == 0) {
            reviewTaskMapper.update(null, Wrappers.<ReviewTask>lambdaUpdate()
                    .eq(ReviewTask::getWrongId, task.getWrongId())
                    .eq(ReviewTask::getStatus, 0)
                    .set(ReviewTask::getStatus, 2));
            List<ReviewTask> replanned = reviewNodePolicy.reset(LocalDate.now(clock),
                    task.getWrongId(), userId);
            for (ReviewTask next : replanned) {
                reviewTaskMapper.insert(next);
            }
        }
        masteryService.recalcForWrong(task.getWrongId());
    }

    /**
     * 跳过本次复习（`API-41` 的跳过分支）—— `status = 2` 且**不重置**周期（`R5`）。
     *
     * @param taskId 复习任务标识
     * @param userId 当前登录学生
     */
    @Transactional
    public void skip(Long taskId, Long userId) {
        requireOwnTask(taskId, userId);
        int rows = reviewTaskMapper.update(null, Wrappers.<ReviewTask>lambdaUpdate()
                .eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getStatus, 0)
                .set(ReviewTask::getStatus, 2)
                .set(ReviewTask::getReviewedAt, LocalDateTime.now(clock)));
        if (rows == 0) {
            throw ApiException.conflict("该复习任务已完成或已跳过，不能重复操作（taskId=" + taskId + "）");
        }
    }

    private ReviewTask requireOwnTask(Long taskId, Long userId) {
        ReviewTask task = reviewTaskMapper.selectOne(Wrappers.<ReviewTask>lambdaQuery()
                .eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getUserId, userId));
        if (task == null) {
            throw ApiException.notFound("复习任务不存在或不属于当前用户：" + taskId);
        }
        return task;
    }
}
