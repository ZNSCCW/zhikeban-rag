package com.zhikeban.bishe.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `HealthController` 单测（`ENG-013`）—— 探活端点的返回体形状（冒烟脚本 `curl` 的落点）与
 * `authenticated` 字段随安全上下文变化的**真实**取值。
 */
class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportsUpAndUnauthenticatedWhenThereIsNoPrincipal() {
        Map<String, Object> body = controller.health();

        assertThat(body).containsEntry("status", "UP")
                .containsEntry("service", "zhikeban-backend")
                .containsEntry("authenticated", false);
    }

    @Test
    void reportsAuthenticatedWhenTheTokenFilterPopulatedTheContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal", null, java.util.List.of()));

        assertThat(controller.health()).containsEntry("authenticated", true);
    }

    @Test
    void anUnverifiedTokenCountsAsNotAuthenticated() {
        // 双参构造的 token 未 setAuthenticated(true) ⇒ isAuthenticated() 为假
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal", "credentials"));

        assertThat(controller.health()).containsEntry("authenticated", false);
    }
}
