package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.domain.entity.WrongBook;
import com.zhikeban.bishe.domain.entity.WrongKp;
import com.zhikeban.bishe.mapper.ReviewTaskMapper;
import com.zhikeban.bishe.mapper.WrongBookMapper;
import com.zhikeban.bishe.mapper.WrongKpMapper;
import com.zhikeban.bishe.support.MybatisLambdaCache;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongCommand;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongUpdateCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `WrongBookService` 单测（`ENG-014`；`§ENG-014` `W`／`M5`／`P` 组的行为面）。
 *
 * <p>★ 覆盖点：**去重取甲**（QUESTION/AI 复用、MANUAL 放行）、**四表事务的写入形状**
 * （错题 + 知识点 + 六条复习任务）、**手动标记初值 0/50/80**、**列表/更新/归档的本人过滤**、
 * 以及**归档关闭未完成任务**。
 */
class WrongBookServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final long USER = 7L;
    private static final long COURSE = 2L;

    private WrongBookMapper wrongBookMapper;
    private WrongKpMapper wrongKpMapper;
    private ReviewTaskMapper reviewTaskMapper;
    private CourseService courseService;
    private WrongBookService service;
    private final AtomicLong ids = new AtomicLong(100L);

    @BeforeAll
    static void warmUpLambdaCache() {
        MybatisLambdaCache.warmUp();
    }

    @BeforeEach
    void setUp() {
        wrongBookMapper = mock(WrongBookMapper.class);
        wrongKpMapper = mock(WrongKpMapper.class);
        reviewTaskMapper = mock(ReviewTaskMapper.class);
        courseService = mock(CourseService.class);
        service = new WrongBookService(wrongBookMapper, wrongKpMapper, reviewTaskMapper,
                courseService, new ReviewNodePolicy(), CLOCK);

        when(courseService.isMember(any(), any())).thenReturn(true);
        doAnswer(invocation -> {
            invocation.getArgument(0, WrongBook.class).setId(ids.getAndIncrement());
            return 1;
        }).when(wrongBookMapper).insert(any(WrongBook.class));
    }

    private static WrongCommand cmd(String sourceType, Long sourceId, String mark, List<Long> kpIds) {
        return new WrongCommand(COURSE, sourceType, sourceId, "1+1 为什么等于 2？",
                "等于 3", "等于 2", "概念不清", mark, kpIds);
    }

    private static WrongBook existing(long id) {
        WrongBook book = new WrongBook();
        book.setId(id);
        book.setUserId(USER);
        book.setStatus(0);
        return book;
    }

    /* ───────── 去重（甲）───────── */

    @Test
    void questionSourceReusesTheExistingRowInsteadOfInsertingAgain() {
        when(wrongBookMapper.selectOne(any())).thenReturn(existing(99L));

        Long id = service.add(USER, cmd("QUESTION", 42L, "未掌握", List.of(3L)));

        assertThat(id).isEqualTo(99L);
        verify(wrongBookMapper, never()).insert(any(WrongBook.class));
        verify(wrongKpMapper, never()).insert(any(WrongKp.class));
        verify(reviewTaskMapper, never()).insert(any(ReviewTask.class));
    }

    @Test
    void manualSourceIsAlwaysAllowedAndGetsAFullReviewPlan() {
        Long id = service.add(USER, cmd("MANUAL", null, "模糊", List.of(3L, 4L)));

        // MANUAL 放行：不去查四元组
        verify(wrongBookMapper, never()).selectOne(any());

        ArgumentCaptor<WrongBook> book = ArgumentCaptor.forClass(WrongBook.class);
        verify(wrongBookMapper).insert(book.capture());
        assertThat(book.getValue().getId()).isEqualTo(id);
        assertThat(book.getValue().getUserId()).isEqualTo(USER);
        assertThat(book.getValue().getCourseId()).isEqualTo(COURSE);
        assertThat(book.getValue().getSourceType()).isEqualTo("MANUAL");
        assertThat(book.getValue().getSourceId()).isNull();
        assertThat(book.getValue().getStatus()).isZero();

        verify(wrongKpMapper, times(2)).insert(any(WrongKp.class));

        ArgumentCaptor<ReviewTask> tasks = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskMapper, times(6)).insert(tasks.capture());
        assertThat(tasks.getAllValues()).extracting(ReviewTask::getStage)
                .containsExactly(1, 2, 4, 7, 15, 30);
        assertThat(tasks.getAllValues()).extracting(ReviewTask::getPlanDate).containsExactly(
                TODAY.plusDays(1), TODAY.plusDays(2), TODAY.plusDays(4),
                TODAY.plusDays(7), TODAY.plusDays(15), TODAY.plusDays(30));
        assertThat(tasks.getAllValues()).allSatisfy(t -> {
            assertThat(t.getUserId()).isEqualTo(USER);
            assertThat(t.getWrongId()).isEqualTo(id);
        });
    }

    @Test
    void lowercaseSourceTypeIsNormalisedBeforeTheDeduplicationLookup() {
        when(wrongBookMapper.selectOne(any())).thenReturn(existing(88L));

        assertThat(service.add(USER, cmd("question", 42L, "未掌握", List.of(3L)))).isEqualTo(88L);
    }

    /* ───────── 初值与校验 ───────── */

    @Test
    void manualMasteryMarkDecidesTheInitialScoreAndLevel() {
        service.add(USER, cmd("MANUAL", null, "未掌握", List.of(3L)));
        service.add(USER, cmd("MANUAL", null, "模糊", List.of(3L)));
        service.add(USER, cmd("MANUAL", null, "已掌握", List.of(3L)));

        ArgumentCaptor<WrongBook> books = ArgumentCaptor.forClass(WrongBook.class);
        verify(wrongBookMapper, times(3)).insert(books.capture());

        assertThat(books.getAllValues()).extracting(WrongBook::getMasteryScore)
                .containsExactly(new BigDecimal("0"), new BigDecimal("50"), new BigDecimal("80"));
        assertThat(books.getAllValues()).extracting(WrongBook::getMasteryLevel)
                .containsExactly(0, 1, 2);
    }

    @Test
    void nonMemberIsRejectedBeforeAnythingIsWritten() {
        when(courseService.isMember(COURSE, USER)).thenReturn(false);

        assertThatThrownBy(() -> service.add(USER, cmd("MANUAL", null, "未掌握", List.of(3L))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        verify(wrongBookMapper, never()).insert(any(WrongBook.class));
        verify(reviewTaskMapper, never()).insert(any(ReviewTask.class));
    }

    @Test
    void knowledgePointsMustBeBetweenOneAndThree() {
        assertThatThrownBy(() -> service.add(USER, cmd("MANUAL", null, "未掌握", List.of())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.add(USER, cmd("MANUAL", null, "未掌握", List.of(1L, 2L, 3L, 4L))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        verify(wrongBookMapper, never()).insert(any(WrongBook.class));
    }

    @Test
    void unknownSourceTypeAndMissingSourceIdAreRejected() {
        assertThatThrownBy(() -> service.add(USER, cmd("WEB", 1L, "未掌握", List.of(3L))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.add(USER, cmd("QUESTION", null, "未掌握", List.of(3L))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        verify(wrongBookMapper, never()).insert(any(WrongBook.class));
    }

    /* ───────── 列表 / 更新 / 归档 ───────── */

    @Test
    void listReturnsWhatTheOwnRowQueryGivesAndAcceptsANullFilter() {
        WrongBook a = existing(1L);
        WrongBook b = existing(2L);
        when(wrongBookMapper.selectList(any())).thenReturn(List.of(a, b));

        assertThat(service.list(USER, null)).hasSize(2);
        assertThat(service.list(USER, null)).containsExactly(a, b);
        verify(wrongBookMapper, times(2)).selectList(any());
    }

    @Test
    void updateRejectsARowOwnedBySomeoneElse() {
        when(wrongBookMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.update(5L, USER, new WrongUpdateCommand("审题偏差", "模糊", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        verify(wrongBookMapper, never()).updateById(any(WrongBook.class));
    }

    @Test
    void updateAppliesTheMarkAndReplacesTheKnowledgePoints() {
        WrongBook book = existing(5L);
        when(wrongBookMapper.selectOne(any())).thenReturn(book);

        service.update(5L, USER, new WrongUpdateCommand("审题偏差", "模糊", List.of(3L, 4L)));

        ArgumentCaptor<WrongBook> updated = ArgumentCaptor.forClass(WrongBook.class);
        verify(wrongBookMapper).updateById(updated.capture());
        assertThat(updated.getValue().getErrorReason()).isEqualTo("审题偏差");
        assertThat(updated.getValue().getMasteryScore()).isEqualByComparingTo("50");
        assertThat(updated.getValue().getMasteryLevel()).isEqualTo(1);

        verify(wrongKpMapper).delete(any());
        verify(wrongKpMapper, times(2)).insert(any(WrongKp.class));
    }

    @Test
    void archiveMarksTheBookArchivedAndClosesItsUnfinishedTasks() {
        when(wrongBookMapper.selectOne(any())).thenReturn(existing(5L));

        service.archive(5L, USER);

        ArgumentCaptor<WrongBook> updated = ArgumentCaptor.forClass(WrongBook.class);
        verify(wrongBookMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(2);

        // 归档同一次事务里关闭未完成任务（status: 0 → 2）
        verify(reviewTaskMapper).update(isNull(), any());
    }

    @Test
    void archiveRejectsARowOwnedBySomeoneElse() {
        when(wrongBookMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.archive(5L, USER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        verify(reviewTaskMapper, never()).update(isNull(), any());
    }
}
