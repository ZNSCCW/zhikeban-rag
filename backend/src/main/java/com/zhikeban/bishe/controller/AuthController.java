package com.zhikeban.bishe.controller;

import com.zhikeban.bishe.security.LoginUser;
import com.zhikeban.bishe.service.AuthService;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.AuthDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 / 用户端点（任务①；`SRS` `FR-1` 子集）。
 *
 * <table>
 *   <caption>本单的四个端点</caption>
 *   <tr><td>{@code POST /api/auth/register}</td><td>注册</td></tr>
 *   <tr><td>{@code POST /api/auth/login}</td><td>登录（签发 Bearer 令牌）</td></tr>
 *   <tr><td>{@code POST /api/auth/logout}</td><td>登出（吊销令牌）</td></tr>
 *   <tr><td>{@code GET  /api/auth/me}</td><td>当前用户</td></tr>
 * </table>
 *
 * <p>★ 本类路径**刻意不含** {@code /chat}、{@code /ai}、{@code /qa}、{@code /ask} 等词
 * —— 那些词是 `§ENG-009` `R2`（"AI/向量端点不存在"）的判定形态。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 注册。 */
    @PostMapping("/register")
    public AuthDtos.UserView register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return authService.register(req);
    }

    /** 登录。 */
    @PostMapping("/login")
    public AuthDtos.LoginResult login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return authService.login(req);
    }

    /** 登出（需带令牌；幂等）。 */
    @PostMapping("/logout")
    public java.util.Map<String, Object> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = (authorization != null && authorization.startsWith(BEARER_PREFIX))
                ? authorization.substring(BEARER_PREFIX.length()).trim() : null;
        authService.logout(token);
        return java.util.Map.of("loggedOut", true);
    }

    /** 当前用户。 */
    @GetMapping("/me")
    public AuthDtos.UserView me(@AuthenticationPrincipal LoginUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("未认证：请先登录并携带 Bearer 令牌");
        }
        return authService.currentUser(principal.getUserId());
    }
}
