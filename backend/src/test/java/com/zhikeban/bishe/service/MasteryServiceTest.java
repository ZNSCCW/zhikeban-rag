package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.domain.entity.WrongBook;
import com.zhikeban.bishe.domain.entity.WrongKp;
import com.zhikeban.bishe.mapper.ReviewTaskMapper;
import com.zhikeban.bishe.mapper.WrongBookMapper;
import com.zhikeban.bishe.mapper.WrongKpMapper;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `MasteryService` 单测（`ENG-014`；`§ENG-014` `M`／`T4` 的行为面）。
 *
 * <p>★ 关键一条：**复习记录 → 单题掌握度**的整条链路必须算出金标 `60.90`
 * （3 次已掌握 + 1 次仍需巩固、最近复习 14 天前）—— 这条断言把
 * "计数口径 / 整天差 / 衰减 / 两位小数"全绑在一起，任何一环写错都会变值。
 */
class MasteryServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final long USER = 7L;
    private static final long COURSE = 2L;
    private static final long WRONG_ID = 5L;

    private WrongBookMapper wrongBookMapper;
    private WrongKpMapper wrongKpMapper;
    private ReviewTaskMapper reviewTaskMapper;
    private MasteryService service;

    @BeforeEach
    void setUp() {
        wrongBookMapper = mock(WrongBookMapper.class);
        wrongKpMapper = mock(WrongKpMapper.class);
        reviewTaskMapper = mock(ReviewTaskMapper.class);
        service = new MasteryService(wrongBookMapper, wrongKpMapper, reviewTaskMapper,
                new MasteryCalculator(), CLOCK);
    }

    private static WrongBook book(long id, String score) {
        WrongBook b = new WrongBook();
        b.setId(id);
        b.setUserId(USER);
        b.setCourseId(COURSE);
        b.setStatus(0);
        b.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        b.setMasteryScore(new BigDecimal(score));
        return b;
    }

    private static ReviewTask reviewed(int result, LocalDateTime at) {
        ReviewTask t = new ReviewTask();
        t.setWrongId(WRONG_ID);
        t.setUserId(USER);
        t.setResult(result);
        t.setReviewedAt(at);
        return t;
    }

    @Test
    void recalcCountsResultsAndAnchorsOnTheLatestReviewDate() {
        WrongBook book = book(WRONG_ID, "0");
        when(wrongBookMapper.selectById(WRONG_ID)).thenReturn(book);
        when(reviewTaskMapper.selectList(any())).thenReturn(List.of(
                reviewed(2, LocalDateTime.of(2026, 9, 26, 10, 0)),
                reviewed(2, LocalDateTime.of(2026, 9, 26, 11, 0)),
                reviewed(2, LocalDateTime.of(2026, 9, 26, 12, 0)),
                reviewed(0, LocalDateTime.of(2026, 9, 20, 9, 0))));

        BigDecimal score = service.recalcForWrong(WRONG_ID);

        // 金标：mastered=3、again=1、reviews=4、Δt = 2026-10-10 − 2026-09-26 = 14 ⇒ 60.90
        assertThat(score).isEqualByComparingTo("60.90");
        assertThat(book.getMasteryScore()).isEqualByComparingTo("60.90");
        assertThat(book.getMasteryLevel()).isEqualTo(1);
        verify(wrongBookMapper).updateById(book);
    }

    @Test
    void neverReviewedBookFallsBackToItsCreationDateForTheDecay() {
        WrongBook book = book(WRONG_ID, "0");
        book.setCreatedAt(LocalDateTime.of(2026, 9, 26, 9, 0));
        when(wrongBookMapper.selectById(WRONG_ID)).thenReturn(book);
        when(reviewTaskMapper.selectList(any())).thenReturn(List.of());

        BigDecimal score = service.recalcForWrong(WRONG_ID);

        // 无复习记录 ⇒ C=0、T=0、基准回退入库日（Δt=14）⇒ 100×0.3×e^(−0.7)=14.8977… ⇒ 14.90
        assertThat(score).isEqualByComparingTo("14.90");
        assertThat(book.getMasteryLevel()).isZero();
    }

    @Test
    void recalcRejectsAMissingWrongBook() {
        when(wrongBookMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.recalcForWrong(99L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        verify(wrongBookMapper, never()).updateById(any(WrongBook.class));
    }

    @Test
    void dailyRecalcUpdatesEveryWrongBookInBatches() {
        WrongBook first = book(1L, "0");
        WrongBook second = book(2L, "0");
        when(wrongBookMapper.selectList(any())).thenReturn(List.of(first, second));
        when(wrongBookMapper.selectById(1L)).thenReturn(first);
        when(wrongBookMapper.selectById(2L)).thenReturn(second);
        when(reviewTaskMapper.selectList(any())).thenReturn(List.of());

        int failures = service.recalcDaily();

        assertThat(failures).isZero();
        verify(wrongBookMapper, times(2)).updateById(any(WrongBook.class));
    }

    @Test
    void knowledgePointMasteryIsTheAverageOfOwnUnarchivedWrongBooks() {
        when(wrongBookMapper.selectList(any())).thenReturn(List.of(
                book(1L, "80"), book(2L, "60"), book(3L, "50")));
        WrongKp first = new WrongKp();
        first.setWrongId(1L);
        first.setKpId(5L);
        WrongKp second = new WrongKp();
        second.setWrongId(2L);
        second.setKpId(5L);
        WrongKp third = new WrongKp();
        third.setWrongId(3L);
        third.setKpId(6L);
        when(wrongKpMapper.selectList(any())).thenReturn(List.of(first, second, third));

        Map<Long, BigDecimal> byKp = service.masteryByKnowledgePoint(COURSE, USER);

        // kp 5 = (80 + 60)/2 = 70.00 ｜ kp 6 = 50.00
        assertThat(byKp).containsEntry(5L, new BigDecimal("70.00"));
        assertThat(byKp).containsEntry(6L, new BigDecimal("50.00"));
        assertThat(byKp.get(5L)).isEqualByComparingTo("70.00");
    }

    @Test
    void knowledgePointStatisticsWithoutDataAreEmptyAndDoNotQueryLinks() {
        when(wrongBookMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.masteryByKnowledgePoint(COURSE, USER)).isEmpty();
        assertThat(service.masteryByKnowledgePoint(null, USER)).isEmpty();
        verify(wrongKpMapper, never()).selectList(any());
    }
}
