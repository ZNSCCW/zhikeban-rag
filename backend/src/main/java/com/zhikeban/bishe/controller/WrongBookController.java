package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.WrongBook;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.WrongBookService;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongCommand;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongQuery;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongUpdateCommand;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 错题本端点（`SRS` `API-36`~`API-39`；`§ENG-014` `F2`／`P1`／`P5`）。
 *
 * <table>
 *   <caption>四条端点与需求映射</caption>
 *   <tr><td><b>新增</b></td><td>{@code POST /api/wrong-book}（`API-36`）</td></tr>
 *   <tr><td><b>列表</b></td><td>{@code GET /api/wrong-book}（`API-37`）</td></tr>
 *   <tr><td><b>更新</b></td><td>{@code PUT /api/wrong-book/{id}}（`API-38`）</td></tr>
 *   <tr><td><b>归档</b></td><td>{@code DELETE /api/wrong-book/{id}}（`API-39`）</td></tr>
 * </table>
 *
 * <p>★★ **每个写端点都从 {@code @AuthenticationPrincipal} 取用户**（`P1`）：
 * **不信任**请求体里的 `userId` —— 越权只能靠"服务端认定是谁"来挡。
 */
@RestController
@RequestMapping("/api/wrong-book")
public class WrongBookController {

    private final WrongBookService wrongBookService;

    public WrongBookController(WrongBookService wrongBookService) {
        this.wrongBookService = wrongBookService;
    }

    /** 错题列表（`API-37`）：只返回本人；默认掌握度升序 + 时间降序。 */
    @GetMapping
    public List<WrongBook> list(@RequestParam(value = "courseId", required = false) Long courseId,
                                @RequestParam(value = "status", required = false) Integer status,
                                @RequestParam(value = "keyword", required = false) String keyword,
                                @AuthenticationPrincipal LoginUser principal) {
        Long userId = requireUserId(principal);
        return wrongBookService.list(userId, new WrongQuery(courseId, status, keyword));
    }

    /** 新增错题（`API-36`）：四种来源由服务层校验，去重取甲。 */
    @PostMapping
    public Map<String, Object> add(@RequestBody WrongCommand req,
                                   @AuthenticationPrincipal LoginUser principal) {
        Long userId = requireUserId(principal);
        Long id = wrongBookService.add(userId, req);
        return Map.of("id", id);
    }

    /** 更新错题（`API-38`）：只有本人能改。 */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable("id") Long id,
                                      @RequestBody WrongUpdateCommand req,
                                      @AuthenticationPrincipal LoginUser principal) {
        Long userId = requireUserId(principal);
        wrongBookService.update(id, userId, req);
        return Map.of("id", id, "updated", true);
    }

    /** 归档错题（`API-39`）：归档即把未完成的复习任务一并关闭。 */
    @DeleteMapping("/{id}")
    public Map<String, Object> archive(@PathVariable("id") Long id,
                                       @AuthenticationPrincipal LoginUser principal) {
        Long userId = requireUserId(principal);
        wrongBookService.archive(id, userId);
        return Map.of("id", id, "archived", true);
    }

    private Long requireUserId(LoginUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("未认证：请先登录");
        }
        return principal.getUserId();
    }
}
