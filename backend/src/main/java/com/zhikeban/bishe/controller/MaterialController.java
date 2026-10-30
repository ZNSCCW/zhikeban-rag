package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.MaterialService;
import com.zhikeban.bishe.web.ApiException;
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

/**
 * 课程资料端点（任务②；`SRS` `FR-2` 子集）。
 *
 * <table>
 *   <caption>本单的端点</caption>
 *   <tr><td>{@code GET  /api/materials}</td><td>列表（可选 {@code ?courseId=}）</td></tr>
 *   <tr><td>{@code POST /api/materials}</td><td>新建（只登记元数据）</td></tr>
 *   <tr><td>{@code GET  /api/materials/{id}}</td><td>详情</td></tr>
 *   <tr><td>{@code PUT  /api/materials/{id}}</td><td>改元数据</td></tr>
 *   <tr><td>{@code DELETE /api/materials/{id}}</td><td>逻辑删除</td></tr>
 * </table>
 *
 * <p>★ **不做上传落地 / 不解析 / 不切片 / 不向量化** —— 见 {@code MaterialService} 的类注释。
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /** 列表。 */
    @GetMapping
    public List<Material> list(@RequestParam(value = "courseId", required = false) Long courseId) {
        return materialService.list(courseId);
    }

    /** 新建（元数据登记）。 */
    @PostMapping
    public Material create(@RequestBody Material req, @AuthenticationPrincipal LoginUser principal) {
        return materialService.create(req, requireUserId(principal));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public Material detail(@PathVariable("id") Long id) {
        return materialService.detail(id);
    }

    /** 改元数据。 */
    @PutMapping("/{id}")
    public Material update(@PathVariable("id") Long id, @RequestBody Material req) {
        return materialService.update(id, req);
    }

    /** 逻辑删除。 */
    @DeleteMapping("/{id}")
    public java.util.Map<String, Object> delete(@PathVariable("id") Long id) {
        materialService.delete(id);
        return java.util.Map.of("deleted", true, "id", id);
    }

    private Long requireUserId(LoginUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("未认证：请先登录");
        }
        return principal.getUserId();
    }
}
