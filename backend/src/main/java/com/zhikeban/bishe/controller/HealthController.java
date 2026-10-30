package com.zhikeban.bishe.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 探活端点（ENG-007 任务①④，可空实现）。
 *
 * <p>★ 这是**冒烟断言**（{@code §ENG-007 C} 组）里那条 {@code curl} 的落点：
 * {@code curl -fsS http://localhost:8080/api/health}。
 *
 * <p>★ {@link SecurityContextHolder} 的引用是 {@code org.springframework.security} 的**第二处**
 * 真实引用（第一处在 {@code config/SecurityConfig}）—— 两处都留着，避免"只有配置类"这种
 * 最容易被误删的单点。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "zhikeban-backend");
        body.put("authenticated", auth != null && auth.isAuthenticated());
        return body;
    }
}
