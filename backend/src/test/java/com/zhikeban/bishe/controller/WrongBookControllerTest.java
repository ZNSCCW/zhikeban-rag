package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.domain.entity.WrongBook;
import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.WrongBookService;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongCommand;
import com.zhikeban.bishe.web.dto.WrongBookDtos.WrongUpdateCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `WrongBookController` 单测（`ENG-014`；`§ENG-014` `P1` 的行为面）。
 *
 * <p>★ 断言点：**用户身份只从 `@AuthenticationPrincipal` 来** —— 未登录一律 401，
 * 已登录时控制器把 `principal.getUserId()`（而**不是**请求体）交给服务层。
 */
class WrongBookControllerTest {

    private static final long USER = 7L;

    private WrongBookService wrongBookService;
    private WrongBookController controller;
    private LoginUser principal;

    @BeforeEach
    void setUp() {
        wrongBookService = mock(WrongBookService.class);
        controller = new WrongBookController(wrongBookService);
        principal = new LoginUser(USER, "student-7");
    }

    private static WrongCommand command() {
        return new WrongCommand(2L, "MANUAL", null, "1+1 为什么等于 2？",
                null, null, "概念不清", "未掌握", List.of(3L));
    }

    @Test
    void listRejectsAnonymousCallers() {
        assertThatThrownBy(() -> controller.list(null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void listUsesThePrincipalUserIdNotAnyRequestParameter() {
        WrongBook book = new WrongBook();
        book.setId(1L);
        when(wrongBookService.list(eq(USER), any())).thenReturn(List.of(book));

        assertThat(controller.list(null, null, null, principal)).hasSize(1);
        verify(wrongBookService).list(eq(USER), any());
    }

    @Test
    void addPassesThePrincipalUserIdToTheService() {
        when(wrongBookService.add(eq(USER), eq(command()))).thenReturn(42L);

        Map<String, Object> body = controller.add(command(), principal);

        assertThat(body).containsEntry("id", 42L);
        verify(wrongBookService).add(USER, command());
    }

    @Test
    void addRejectsAnonymousCallers() {
        assertThatThrownBy(() -> controller.add(command(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
        verify(wrongBookService, never()).add(USER, command());
    }

    @Test
    void updatePassesIdAndPrincipalUserId() {
        WrongUpdateCommand update = new WrongUpdateCommand("审题偏差", "模糊", List.of(3L));

        Map<String, Object> body = controller.update(5L, update, principal);

        assertThat(body).containsEntry("id", 5L).containsEntry("updated", true);
        verify(wrongBookService).update(5L, USER, update);
    }

    @Test
    void archivePassesIdAndPrincipalUserId() {
        Map<String, Object> body = controller.archive(5L, principal);

        assertThat(body).containsEntry("id", 5L).containsEntry("archived", true);
        verify(wrongBookService).archive(5L, USER);
    }

    @Test
    void archiveRejectsAnonymousCallers() {
        assertThatThrownBy(() -> controller.archive(5L, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
        verify(wrongBookService, never()).archive(5L, USER);
    }
}
