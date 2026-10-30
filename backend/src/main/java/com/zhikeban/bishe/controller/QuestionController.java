package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.Answer;
import com.zhikeban.bishe.domain.entity.Question;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.CommunityService;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 问答·**社区面**端点（任务③；`SRS` `FR-3` 最小可用面：**发帖 / 回复 / 采纳**）。
 *
 * <table>
 *   <caption>三类社区面端点（`STANDARDS.md` §ENG-009 的 `R6` 判"三类各自 ≥1"）</caption>
 *   <tr><td><b>发帖</b></td><td>{@code GET /api/questions}、{@code POST /api/questions}</td></tr>
 *   <tr><td><b>回复</b></td><td>{@code GET /api/questions/{id}/answers}、{@code POST /api/questions/{id}/answers}</td></tr>
 *   <tr><td><b>采纳</b></td><td>{@code POST /api/questions/{id}/accept}</td></tr>
 * </table>
 *
 * <p>★★ **不做 AI 问答**：本类里**没有**任何生成 / 嵌入 / 检索调用，也**不暴露**任何
 * 智能问答端点（{@code §ENG-009} 边界 (b)，`R2`/`R4` 的判据形态）。
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final CommunityService communityService;

    public QuestionController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /* ── 发帖 ───────────────────────────────────────────────── */

    /** 帖子列表（可选 {@code ?courseId=}）。 */
    @GetMapping
    public List<Question> list(@RequestParam(value = "courseId", required = false) Long courseId) {
        return communityService.listQuestions(courseId);
    }

    /** 发帖。 */
    @PostMapping
    public Question create(@RequestBody Question req, @AuthenticationPrincipal LoginUser principal) {
        return communityService.createQuestion(req, requireUserId(principal));
    }

    /** 帖子详情（含回复列表）。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable("id") Long id) {
        return communityService.questionDetail(id);
    }

    /* ── 回复 ───────────────────────────────────────────────── */

    /** 某帖子的回复列表。 */
    @GetMapping("/{id}/answers")
    public List<Answer> listAnswers(@PathVariable("id") Long id) {
        return communityService.listAnswers(id);
    }

    /** 回复（发回答）。 */
    @PostMapping("/{id}/answers")
    public Answer reply(@PathVariable("id") Long id, @RequestBody Answer req,
                        @AuthenticationPrincipal LoginUser principal) {
        return communityService.createAnswer(id, req, requireUserId(principal));
    }

    /* ── 采纳 ───────────────────────────────────────────────── */

    /** 采纳某个回答（只有提问者本人；每题唯一）。 */
    @PostMapping("/{id}/accept")
    public Map<String, Object> accept(@PathVariable("id") Long id,
                                      @RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal LoginUser principal) {
        Object raw = body == null ? null : body.get("answerId");
        if (raw == null) {
            throw ApiException.badRequest("answerId 不能为空");
        }
        Long answerId;
        try {
            answerId = Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("answerId 必须是数字：" + raw);
        }
        return communityService.accept(id, answerId, requireUserId(principal));
    }

    private Long requireUserId(LoginUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("未认证：请先登录");
        }
        return principal.getUserId();
    }
}
