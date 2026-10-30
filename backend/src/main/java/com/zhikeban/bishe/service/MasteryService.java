package com.zhikeban.bishe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.domain.entity.WrongBook;
import com.zhikeban.bishe.domain.entity.WrongKp;
import com.zhikeban.bishe.mapper.ReviewTaskMapper;
import com.zhikeban.bishe.mapper.WrongBookMapper;
import com.zhikeban.bishe.mapper.WrongKpMapper;
import com.zhikeban.bishe.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 掌握度服务（`SRS` `FR-5-05`／`FR-5-10`；`API-42`；详设 §4.4；`§ENG-014` `M` 组）。
 *
 * <p>★★ **两个粒度**（详设 §3.5 设计决定 3）：单题分写回 `wrong_book.mastery_score` /
 * `mastery_level`；知识点聚合掌握度**不额外落表**，由 `TB-15` + `TB-16` 现算（本类的
 * {@link #masteryByKnowledgePoint(Long, Long)} 就是 API-42 的实现）。
 *
 * <p>★ 计算一律委托给无状态纯函数 {@link MasteryCalculator}，基准时刻由 {@link Clock} **注入**
 * （`§ENG-014` `A-5`／`M6`：纯函数体内不得 `.now()`）。
 */
@Service
public class MasteryService {

    private static final Logger log = LoggerFactory.getLogger(MasteryService.class);

    /** 批量重算的分批大小（详设 §4.4.4：每批 200 条，失败可重跑）。 */
    private static final int BATCH_SIZE = 200;

    private final WrongBookMapper wrongBookMapper;
    private final WrongKpMapper wrongKpMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final MasteryCalculator masteryCalculator;
    private final Clock clock;

    public MasteryService(WrongBookMapper wrongBookMapper, WrongKpMapper wrongKpMapper,
                          ReviewTaskMapper reviewTaskMapper, MasteryCalculator masteryCalculator,
                          Clock clock) {
        this.wrongBookMapper = wrongBookMapper;
        this.wrongKpMapper = wrongKpMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.masteryCalculator = masteryCalculator;
        this.clock = clock;
    }

    /**
     * 单题重算（复习后实时更新；详设 §4.4.4）。
     *
     * <p>★ 输入全部来自库上事实：`result` 计数、累计复习次数、最近一次复习日；
     * **从未复习**时基准回退到入库日（`M2`）。
     *
     * @param wrongId 错题标识
     * @return 重算后的分值（两位小数）
     */
    @Transactional
    public BigDecimal recalcForWrong(Long wrongId) {
        WrongBook book = wrongBookMapper.selectById(wrongId);
        if (book == null) {
            throw ApiException.notFound("错题不存在：" + wrongId);
        }
        List<ReviewTask> tasks = reviewTaskMapper.selectList(Wrappers.<ReviewTask>lambdaQuery()
                .eq(ReviewTask::getWrongId, wrongId));
        int mastered = 0;
        int again = 0;
        int reviews = 0;
        LocalDate lastReviewed = null;
        for (ReviewTask task : tasks == null ? List.<ReviewTask>of() : tasks) {
            if (task.getResult() == null) {
                continue;
            }
            reviews++;
            if (task.getResult() == 2) {
                mastered++;
            } else if (task.getResult() == 0) {
                again++;
            }
            if (task.getReviewedAt() != null) {
                LocalDate day = task.getReviewedAt().toLocalDate();
                if (lastReviewed == null || day.isAfter(lastReviewed)) {
                    lastReviewed = day;
                }
            }
        }
        LocalDate createdAt = book.getCreatedAt() == null ? null : book.getCreatedAt().toLocalDate();
        MasteryInput input = new MasteryInput(mastered, again, reviews, lastReviewed, createdAt);
        BigDecimal score = masteryCalculator.calc(input, MasteryParams.DEFAULT, LocalDate.now(clock));

        book.setMasteryScore(score);
        book.setMasteryLevel(masteryCalculator.level(score));
        wrongBookMapper.updateById(book);
        return score;
    }

    /**
     * 每日批量重算（`FR-5-05`；详设 §4.4.4：分批提交、失败可重跑、幂等）。
     *
     * <p>★ 单条失败**不中断整批**：跳过该条并累积失败计数，任务结束汇总告警（详设 §4.4.6）。
     *
     * @return 失败条数（0 表示全部成功）
     */
    public int recalcDaily() {
        List<WrongBook> books = wrongBookMapper.selectList(Wrappers.<WrongBook>lambdaQuery()
                .orderByAsc(WrongBook::getId));
        if (books == null || books.isEmpty()) {
            return 0;
        }
        int failures = 0;
        int from = 0;
        while (from < books.size()) {
            int to = Math.min(from + BATCH_SIZE, books.size());
            for (WrongBook book : books.subList(from, to)) {
                try {
                    recalcForWrong(book.getId());
                } catch (RuntimeException e) {
                    failures++;
                    log.warn("掌握度批量重算失败（跳过该条）：wrongId={} ｜ {}", book.getId(), e.getMessage());
                }
            }
            from = to;
        }
        if (failures > 0) {
            log.warn("掌握度批量重算结束：失败 {} 条（可重跑，重算幂等）", failures);
        }
        return failures;
    }

    /**
     * 知识点掌握度聚合（`API-42`；`FR-5-10`）—— **只对本人、本人课程**做聚合。
     *
     * <p>★ 聚合口径：同一知识点下**未归档**错题的分值**算术平均**（两位小数 `HALF_UP`）；
     * 不落新表（详设 §4.4.4）。
     *
     * @param courseId 课程标识
     * @param userId   当前登录学生
     * @return 知识点标识 → 聚合掌握度（无数据时返回空表）
     */
    public Map<Long, BigDecimal> masteryByKnowledgePoint(Long courseId, Long userId) {
        if (courseId == null || userId == null) {
            return Map.of();
        }
        List<WrongBook> books = wrongBookMapper.selectList(Wrappers.<WrongBook>lambdaQuery()
                .eq(WrongBook::getUserId, userId)
                .eq(WrongBook::getCourseId, courseId)
                .ne(WrongBook::getStatus, 2));
        if (books == null || books.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> scoreById = new LinkedHashMap<>();
        for (WrongBook book : books) {
            scoreById.put(book.getId(), book.getMasteryScore() == null ? BigDecimal.ZERO : book.getMasteryScore());
        }
        List<WrongKp> links = wrongKpMapper.selectList(Wrappers.<WrongKp>lambdaQuery()
                .in(WrongKp::getWrongId, new ArrayList<>(scoreById.keySet())));
        Map<Long, List<BigDecimal>> grouped = new LinkedHashMap<>();
        for (WrongKp link : links == null ? List.<WrongKp>of() : links) {
            BigDecimal score = scoreById.get(link.getWrongId());
            if (score == null || link.getKpId() == null) {
                continue;
            }
            grouped.computeIfAbsent(link.getKpId(), k -> new ArrayList<>()).add(score);
        }
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        grouped.forEach((kpId, scores) -> {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal score : scores) {
                sum = sum.add(score);
            }
            out.put(kpId, sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
        });
        return out;
    }
}
