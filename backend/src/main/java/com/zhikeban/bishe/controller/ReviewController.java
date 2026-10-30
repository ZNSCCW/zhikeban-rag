package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.ReviewService;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.WrongBookDtos.ReviewCompleteCommand;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 复习端点（`SRS` `API-40`／`API-41`；`§ENG-014` `F2`／`P1`／`P4`）。
 *
 * <table>
 *   <caption>三条端点</caption>
 *   <tr><td><b>今日复习</b></td><td>{@code GET /api/review/today}（`API-40`，**只读**）</td></tr>
 *   <tr><td><b>提交结果</b></td><td>{@code POST /api/review/{taskId}/complete}（`API-41`）</td></tr>
 *   <tr><td><b>跳过</b></td><td>{@code POST /api/review/{taskId}/skip}（`API-41` 的跳过分支）</td></tr>
 * </table>
 *
 * <p>★ 写端点一律从 {@code @AuthenticationPrincipal} 取用户（`P1`），服务层再按 `user_id` 过滤。
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 今日复习任务（`API-40`）：只读，只返回本人到期的未完成项。 */
    @GetMapping("/today")
    public List<ReviewTask> today(@AuthenticationPrincipal LoginUser principal) {
        return reviewService.today(requireUserId(principal));
    }

    /** 提交复习结果（`API-41`）：「仍需巩固」(0) 会重置该题周期。 */
    @PostMapping("/{taskId}/complete")
    public Map<String, Object> complete(@PathVariable("taskId") Long taskId,
                                        @RequestBody(required = false) ReviewCompleteCommand req,
                                        @AuthenticationPrincipal LoginUser principal) {
        Long userId = requireUserId(principal);
        reviewService.complete(taskId, userId, req == null ? null : req.result());
        return Map.of("taskId", taskId, "completed", true);
    }

    /** 跳过本次复习（`API-41`）：`status = 2`，**不重置**周期。 */
    @PostMapping("/{taskId}/skip")
    public Map<String, Object> skip(@PathVariable("taskId") Long taskId,
                                    @AuthenticationPrincipal LoginUser principal) {
        Long userId = requireUserId(principal);
        reviewService.skip(taskId, userId);
        return Map.of("taskId", taskId, "skipped", true);
    }

    private Long requireUserId(LoginUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("未认证：请先登录");
        }
        return principal.getUserId();
    }
}
