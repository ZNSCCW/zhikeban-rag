package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.MasteryService;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习统计端点（`SRS` `API-42`；`§ENG-014` `F2`／`P1`／`P5`）。
 *
 * <p>★ 本单只做 **`API-42` 知识点掌握度**（`FR-5-10` 的学生视角）：聚合由
 * {@link MasteryService#masteryByKnowledgePoint(Long, Long)} 现算，**不落新表**（详设 §4.4.4）。
 * 错题趋势（`API-43`）／课程学情（`API-44`）属后续编码单，本单不做。
 *
 * <p>★ 只返回**本人**的聚合（`P5`）：`userId` 取自登录主体，**不接受**请求参数传入。
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final MasteryService masteryService;

    public StatisticsController(MasteryService masteryService) {
        this.masteryService = masteryService;
    }

    /** 知识点掌握度（`API-42`）：`{courseId, userId, points:[{kpId, mastery}]}`。 */
    @GetMapping("/knowledge")
    public Map<String, Object> knowledge(@RequestParam("courseId") Long courseId,
                                         @AuthenticationPrincipal LoginUser principal) {
        Long userId = requireUserId(principal);
        Map<Long, BigDecimal> byKp = masteryService.masteryByKnowledgePoint(courseId, userId);
        List<Map<String, Object>> points = new ArrayList<>();
        byKp.forEach((kpId, mastery) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kpId", kpId);
            item.put("mastery", mastery);
            points.add(item);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("courseId", courseId);
        out.put("userId", userId);
        out.put("points", points);
        return out;
    }

    private Long requireUserId(LoginUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("未认证：请先登录");
        }
        return principal.getUserId();
    }
}
