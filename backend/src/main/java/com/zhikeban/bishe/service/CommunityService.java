package com.zhikeban.bishe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikeban.bishe.domain.entity.Answer;
import com.zhikeban.bishe.domain.entity.Question;
import com.zhikeban.bishe.mapper.AnswerMapper;
import com.zhikeban.bishe.mapper.QuestionMapper;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答·**社区面**服务（任务③；`SRS` `FR-3` 的最小可用面：**发帖 / 回复 / 采纳**）。
 *
 * <p>★★ 边界（{@code ORDERS.md} 一之十五 第 ② 行）：**只做社区面，不做 AI 问答** ——
 * 本类与 {@code QuestionController} 里**没有任何**生成 / 嵌入 / 检索调用，只有
 * 社区内容的增删查改。`STANDARDS.md` §ENG-009 的 `R2`~`R5` 判的就是"**有没有接通**"。
 *
 * <p>★ 采纳是**每题唯一**的（概设 §4.4 {@code question.accepted_answer_id} 单值）⇒
 * 重复采纳报 409；只有**提问者本人**能采纳（否则 403）。
 */
@Service
public class CommunityService {

    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;

    public CommunityService(QuestionMapper questionMapper, AnswerMapper answerMapper) {
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
    }

    /* ───────────────────────── 发帖（提问）───────────────────────── */

    /** 提问列表（可按课程过滤；置顶优先、再按新→旧）。 */
    public List<Question> listQuestions(Long courseId) {
        return questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .eq(courseId != null, Question::getCourseId, courseId)
                .orderByDesc(Question::getIsTop)
                .orderByDesc(Question::getId));
    }

    /** 发帖。 */
    public Question createQuestion(Question req, Long authorId) {
        if (req.getCourseId() == null) {
            throw ApiException.badRequest("courseId 不能为空");
        }
        if (req.getTitle() == null || req.getTitle().trim().length() < 5) {
            throw ApiException.badRequest("标题至少 5 个字");
        }
        if (req.getContent() == null || req.getContent().trim().length() < 10) {
            throw ApiException.badRequest("正文至少 10 个字");
        }
        Question q = new Question();
        q.setCourseId(req.getCourseId());
        q.setAuthorId(authorId);
        q.setTitle(req.getTitle().trim());
        q.setContent(req.getContent());
        q.setImages(req.getImages());
        q.setStatus(0);
        q.setIsTop(0);
        q.setIsEssence(0);
        q.setViewCount(0);
        q.setAnswerCount(0);
        q.setDeleted(0);
        questionMapper.insert(q);
        return q;
    }

    /** 帖子详情 + 回复列表（一并返回，前端一次请求即可渲染）。 */
    public Map<String, Object> questionDetail(Long id) {
        Question q = requireQuestion(id);
        List<Answer> answers = answerMapper.selectList(Wrappers.<Answer>lambdaQuery()
                .eq(Answer::getQuestionId, id)
                .orderByAsc(Answer::getId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("question", q);
        out.put("answers", answers);
        return out;
    }

    /* ───────────────────────── 回复（回答）───────────────────────── */

    /** 某帖子的回复列表。 */
    public List<Answer> listAnswers(Long questionId) {
        requireQuestion(questionId);
        return answerMapper.selectList(Wrappers.<Answer>lambdaQuery()
                .eq(Answer::getQuestionId, questionId)
                .orderByAsc(Answer::getId));
    }

    /** 回复（写回答 + 提问的冗余计数 +1）。 */
    @Transactional
    public Answer createAnswer(Long questionId, Answer req, Long authorId) {
        Question q = requireQuestion(questionId);
        if (req.getContent() == null || req.getContent().trim().length() < 2) {
            throw ApiException.badRequest("回复内容至少 2 个字");
        }
        Answer a = new Answer();
        a.setQuestionId(questionId);
        a.setAuthorId(authorId);
        a.setContent(req.getContent());
        a.setImages(req.getImages());
        a.setIsAccepted(0);
        a.setVoteUp(0);
        a.setVoteDown(0);
        a.setDeleted(0);
        answerMapper.insert(a);

        q.setAnswerCount((q.getAnswerCount() == null ? 0 : q.getAnswerCount()) + 1);
        questionMapper.updateById(q);
        return a;
    }

    /* ───────────────────────── 采纳 ───────────────────────── */

    /**
     * 采纳某个回答（**只有提问者本人**；每题唯一；采纳后提问状态置"已解决"）。
     *
     * <p>★ 两处落点同改：{@code answer.is_accepted = 1} 与
     * {@code question.accepted_answer_id = <id>} + {@code question.status = 1}。
     */
    @Transactional
    public Map<String, Object> accept(Long questionId, Long answerId, Long operatorId) {
        Question q = requireQuestion(questionId);
        if (!q.getAuthorId().equals(operatorId)) {
            throw ApiException.forbidden("只有提问者本人可以采纳回答");
        }
        if (q.getAcceptedAnswerId() != null) {
            throw ApiException.conflict("该提问已采纳过回答（answerId=" + q.getAcceptedAnswerId() + "）");
        }
        Answer a = answerMapper.selectById(answerId);
        if (a == null || !questionId.equals(a.getQuestionId())) {
            throw ApiException.notFound("该提问下不存在这个回答：" + answerId);
        }
        a.setIsAccepted(1);
        answerMapper.updateById(a);

        q.setAcceptedAnswerId(answerId);
        q.setStatus(1);
        questionMapper.updateById(q);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionId", questionId);
        out.put("acceptedAnswerId", answerId);
        out.put("status", q.getStatus());
        return out;
    }

    private Question requireQuestion(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw ApiException.notFound("提问不存在：" + id);
        }
        return q;
    }
}
