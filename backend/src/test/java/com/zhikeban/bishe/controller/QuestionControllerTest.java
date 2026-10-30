package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.Answer;
import com.zhikeban.bishe.domain.entity.Question;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.CommunityService;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `QuestionController` 单测（`ENG-013`）—— 社区面三类端点：**发帖 / 回复 / 采纳**。
 * ★ 采纳的 `answerId` 解析（缺参 / 非数字 ⇒ 400）是本控制器唯一的入参逻辑。
 */
class QuestionControllerTest {

    private CommunityService communityService;
    private QuestionController controller;

    @BeforeEach
    void setUp() {
        communityService = mock(CommunityService.class);
        controller = new QuestionController(communityService);
    }

    private static Question question(long id) {
        Question q = new Question();
        q.setId(id);
        q.setAuthorId(5L);
        return q;
    }

    private static Answer answer(long id) {
        Answer a = new Answer();
        a.setId(id);
        a.setQuestionId(1L);
        return a;
    }

    @Test
    void listCreateDetailAndAnswersDelegate() {
        when(communityService.listQuestions(2L)).thenReturn(List.of(question(1L)));
        assertThat(controller.list(2L)).hasSize(1);

        Question req = question(0L);
        when(communityService.createQuestion(req, 7L)).thenReturn(question(1L));
        assertThat(controller.create(req, new LoginUser(7L, "user-7")).getId()).isEqualTo(1L);

        when(communityService.questionDetail(1L)).thenReturn(Map.of("question", question(1L), "answers", List.of()));
        assertThat(controller.detail(1L)).containsKey("question");

        when(communityService.listAnswers(1L)).thenReturn(List.of(answer(11L)));
        assertThat(controller.listAnswers(1L)).hasSize(1);
    }

    @Test
    void createAndReplyRequireAuthentication() {
        assertThatThrownBy(() -> controller.create(new Question(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));

        assertThatThrownBy(() -> controller.reply(1L, new Answer(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void replyDelegatesToTheService() {
        Answer req = answer(0L);
        when(communityService.createAnswer(1L, req, 7L)).thenReturn(answer(11L));

        assertThat(controller.reply(1L, req, new LoginUser(7L, "user-7")).getId()).isEqualTo(11L);
    }

    @Test
    void acceptRequiresANumericAnswerId() {
        assertThatThrownBy(() -> controller.accept(1L, null, new LoginUser(5L, "user-5")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));

        assertThatThrownBy(() -> controller.accept(1L, Map.of(), new LoginUser(5L, "user-5")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));

        assertThatThrownBy(() -> controller.accept(1L, Map.of("answerId", "abc"), new LoginUser(5L, "user-5")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void acceptRequiresAuthentication() {
        assertThatThrownBy(() -> controller.accept(1L, Map.of("answerId", 11), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void acceptParsesNumericAndStringAnswerIds() {
        when(communityService.accept(1L, 11L, 5L))
                .thenReturn(Map.of("questionId", 1L, "acceptedAnswerId", 11L, "status", 1));

        assertThat(controller.accept(1L, Map.of("answerId", 11), new LoginUser(5L, "user-5")))
                .containsEntry("acceptedAnswerId", 11L);
        assertThat(controller.accept(1L, Map.of("answerId", "11"), new LoginUser(5L, "user-5")))
                .containsEntry("acceptedAnswerId", 11L);
    }
}
