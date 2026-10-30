package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.AuthService;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `AuthController` 单测（`ENG-013`）—— 四个端点：注册 / 登录 / 登出 / 当前用户。
 * ★ 登出对**缺失或非法**的 Authorization 头一律传 `null`（幂等，不泄露令牌是否存在）。
 */
class AuthControllerTest {

    private AuthService authService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        controller = new AuthController(authService);
    }

    @Test
    void registerAndLoginDelegateToTheService() {
        AuthDtos.RegisterRequest register = new AuthDtos.RegisterRequest("alice", "pw123456", null, null, null, null);
        AuthDtos.UserView view = new AuthDtos.UserView(1L, "alice", "小艾", null, null, null, 1);
        when(authService.register(register)).thenReturn(view);
        assertThat(controller.register(register)).isSameAs(view);

        AuthDtos.LoginRequest login = new AuthDtos.LoginRequest("alice", "pw123456");
        AuthDtos.LoginResult result = new AuthDtos.LoginResult("token", "Bearer", view);
        when(authService.login(login)).thenReturn(result);
        assertThat(controller.login(login)).isSameAs(result);
    }

    @Test
    void logoutStripsTheBearerPrefix() {
        controller.logout("Bearer abc123");

        verify(authService).logout("abc123");
        assertThat(controller.logout("Bearer   spaced  ")).containsEntry("loggedOut", true);
        verify(authService).logout("spaced");
    }

    @Test
    void logoutPassesNullWhenTheHeaderIsMissingOrUsesAnotherScheme() {
        Map<String, Object> body = controller.logout(null);
        verify(authService).logout(null);
        assertThat(body).containsEntry("loggedOut", true);

        controller.logout("Token abc");
        verify(authService, org.mockito.Mockito.times(2)).logout(null);
    }

    @Test
    void meRequiresAnAuthenticatedPrincipal() {
        assertThatThrownBy(() -> controller.me(null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void meReturnsTheCurrentUserView() {
        AuthDtos.UserView view = new AuthDtos.UserView(1L, "alice", "小艾", null, null, null, 1);
        when(authService.currentUser(1L)).thenReturn(view);

        assertThat(controller.me(new LoginUser(1L, "user-1"))).isSameAs(view);
    }
}
