package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.Course;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.CourseService;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程端点（任务②；`SRS` `FR-2` 子集：最小 CRUD / 列表）。
 *
 * <table>
 *   <caption>本单的端点</caption>
 *   <tr><td>{@code GET  /api/courses}</td><td>列表（可选 {@code ?teacherId=}）</td></tr>
 *   <tr><td>{@code POST /api/courses}</td><td>新建</td></tr>
 *   <tr><td>{@code GET  /api/courses/{id}}</td><td>详情</td></tr>
 *   <tr><td>{@code PUT|PATCH /api/courses/{id}}</td><td>改基本信息</td></tr>
 *   <tr><td>{@code DELETE /api/courses/{id}}</td><td>逻辑删除</td></tr>
 * </table>
 *
 * <p>★ 本类路径不含 {@code R2} 的禁用词（`/chat`、`/ai`、`/qa`、`/ask`、`/rag`、
 * `/retrieval`、`/vector`、`/embedding`、`/knowledge`）。
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /** 列表。 */
    @GetMapping
    public List<Course> list(@RequestParam(value = "teacherId", required = false) Long teacherId) {
        return courseService.list(teacherId);
    }

    /** 新建。 */
    @PostMapping
    public Course create(@RequestBody Course req, @AuthenticationPrincipal LoginUser principal) {
        return courseService.create(req, requireUserId(principal));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public Course detail(@PathVariable("id") Long id) {
        return courseService.detail(id);
    }

    /** 改基本信息（PUT 与 PATCH 同义；骨架期不区分全量/增量）。 */
    @PutMapping("/{id}")
    public Course update(@PathVariable("id") Long id, @RequestBody Course req) {
        return courseService.update(id, req);
    }

    /** 改基本信息（PATCH 别名）。 */
    @PatchMapping("/{id}")
    public Course patch(@PathVariable("id") Long id, @RequestBody Course req) {
        return courseService.update(id, req);
    }

    /** 逻辑删除。 */
    @DeleteMapping("/{id}")
    public java.util.Map<String, Object> delete(@PathVariable("id") Long id) {
        courseService.delete(id);
        return java.util.Map.of("deleted", true, "id", id);
    }

    private Long requireUserId(LoginUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("未认证：请先登录");
        }
        return principal.getUserId();
    }
}
