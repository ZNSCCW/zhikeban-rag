package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.Answer;
import com.zhikeban.bishe.domain.entity.Question;
import com.zhikeban.bishe.mapper.AnswerMapper;
import com.zhikeban.bishe.mapper.QuestionMapper;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `CommunityService` 单测（`ENG-013`）—— 社区面三件事：**发帖 / 回复 / 采纳**。
 * ★ 采纳的约束是**业务规则**：只有提问者本人、每题唯一、回答必须属于该提问。
 */
class CommunityServiceTest {

    private QuestionMapper questionMapper;
    private AnswerMapper answerMapper;
    private CommunityService service;

    @BeforeEach
    void setUp() {
        questionMapper = mock(QuestionMapper.class);
        answerMapper = mock(AnswerMapper.class);
        service = new CommunityService(questionMapper, answerMapper);
    }

    private static Question question(long id, long authorId, Long acceptedAnswerId) {
        Question q = new Question();
        q.setId(id);
        q.setCourseId(2L);
        q.setAuthorId(authorId);
        q.setTitle("标题至少五个字");
        q.setContent("正文至少十个字的内容");
        q.setAnswerCount(0);
        q.setAcceptedAnswerId(acceptedAnswerId);
        return q;
    }

    private static Answer answer(long id, long questionId) {
        Answer a = new Answer();
        a.setId(id);
        a.setQuestionId(questionId);
        a.setContent("回复内容");
        return a;
    }

    /* ── 发帖 ─────────────────────────────────────────── */

    @Test
    void listQuestionsDelegatesToMapper() {
        when(questionMapper.selectList(any())).thenReturn(List.of(question(1L, 5L, null)));

        assertThat(service.listQuestions(2L)).hasSize(1);
        assertThat(service.listQuestions(null)).hasSize(1);
    }

    @Test
    void createQuestionValidatesCourseTitleAndContentLength() {
        assertThatThrownBy(() -> service.createQuestion(new Question(), 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));

        Question shortTitle = new Question();
        shortTitle.setCourseId(2L);
        shortTitle.setTitle("四个字");
        shortTitle.setContent("正文至少十个字的内容");
        assertThatThrownBy(() -> service.createQuestion(shortTitle, 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));

        Question shortContent = new Question();
        shortContent.setCourseId(2L);
        shortContent.setTitle("标题至少五个字");
        shortContent.setContent("太短");
        assertThatThrownBy(() -> service.createQuestion(shortContent, 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void createQuestionTrimsTitleAndInitialisesCounters() {
        Question req = new Question();
        req.setCourseId(2L);
        req.setTitle("  标题至少五个字  ");
        req.setContent("正文至少十个字的内容");

        Question created = service.createQuestion(req, 5L);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).insert(captor.capture());
        Question saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("标题至少五个字");
        assertThat(saved.getAuthorId()).isEqualTo(5L);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getIsTop()).isZero();
        assertThat(saved.getIsEssence()).isZero();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getAnswerCount()).isZero();
        assertThat(saved.getDeleted()).isZero();
        assertThat(created).isSameAs(saved);
    }

    @Test
    void questionDetailReturnsQuestionAndAnswersTogether() {
        when(questionMapper.selectById(1L)).thenReturn(question(1L, 5L, null));
        when(answerMapper.selectList(any())).thenReturn(List.of(answer(11L, 1L)));

        Map<String, Object> out = service.questionDetail(1L);

        assertThat(out).containsKeys("question", "answers");
        assertThat(out.get("answers")).isInstanceOf(List.class);
        assertThat((List<?>) out.get("answers")).hasSize(1);
    }

    @Test
    void questionDetailThrows404WhenMissing() {
        when(questionMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.questionDetail(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    /* ── 回复 ─────────────────────────────────────────── */

    @Test
    void listAnswersRequiresTheQuestionToExist() {
        when(questionMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.listAnswers(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        when(questionMapper.selectById(1L)).thenReturn(question(1L, 5L, null));
        when(answerMapper.selectList(any())).thenReturn(List.of(answer(11L, 1L)));
        assertThat(service.listAnswers(1L)).hasSize(1);
    }

    @Test
    void createAnswerValidatesContentAndBumpsQuestionCounter() {
        when(questionMapper.selectById(1L)).thenReturn(question(1L, 5L, null));

        Answer tooShort = new Answer();
        tooShort.setContent("短");
        assertThatThrownBy(() -> service.createAnswer(1L, tooShort, 6L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));

        Answer req = new Answer();
        req.setContent("够长的回复");
        Answer created = service.createAnswer(1L, req, 6L);

        assertThat(created.getAuthorId()).isEqualTo(6L);
        assertThat(created.getIsAccepted()).isZero();
        assertThat(created.getVoteUp()).isZero();
        assertThat(created.getVoteDown()).isZero();
        assertThat(created.getDeleted()).isZero();

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAnswerCount()).isEqualTo(1);
    }

    @Test
    void createAnswerThrows404WhenQuestionMissing() {
        when(questionMapper.selectById(9L)).thenReturn(null);
        Answer req = new Answer();
        req.setContent("够长的回复");

        assertThatThrownBy(() -> service.createAnswer(9L, req, 6L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    /* ── 采纳 ─────────────────────────────────────────── */

    @Test
    void onlyTheAuthorMayAccept() {
        when(questionMapper.selectById(1L)).thenReturn(question(1L, 5L, null));

        assertThatThrownBy(() -> service.accept(1L, 11L, 6L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
    }

    @Test
    void aQuestionCanOnlyBeAcceptedOnce() {
        when(questionMapper.selectById(1L)).thenReturn(question(1L, 5L, 11L));

        assertThatThrownBy(() -> service.accept(1L, 11L, 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void theAnswerMustBelongToThatQuestion() {
        when(questionMapper.selectById(1L)).thenReturn(question(1L, 5L, null));
        when(answerMapper.selectById(11L)).thenReturn(null);
        assertThatThrownBy(() -> service.accept(1L, 11L, 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        when(answerMapper.selectById(12L)).thenReturn(answer(12L, 99L));
        assertThatThrownBy(() -> service.accept(1L, 12L, 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void acceptWritesBothSidesAndMarksTheQuestionSolved() {
        Question q = question(1L, 5L, null);
        when(questionMapper.selectById(1L)).thenReturn(q);
        when(answerMapper.selectById(11L)).thenReturn(answer(11L, 1L));

        Map<String, Object> out = service.accept(1L, 11L, 5L);

        ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
        verify(answerMapper).updateById(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getIsAccepted()).isEqualTo(1);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).updateById(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getAcceptedAnswerId()).isEqualTo(11L);
        assertThat(questionCaptor.getValue().getStatus()).isEqualTo(1);

        assertThat(out).containsEntry("questionId", 1L)
                .containsEntry("acceptedAnswerId", 11L)
                .containsEntry("status", 1);
    }
}
