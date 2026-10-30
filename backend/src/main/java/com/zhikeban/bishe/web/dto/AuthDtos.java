package com.zhikeban.bishe.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 认证类请求体（任务①：注册 / 登录）。
 *
 * <p>★ 用 {@code record} 承载请求，避免为每个字段写样板 getter/setter；
 * 校验注解由 {@code spring-boot-starter-validation} 驱动，控制器上带 {@code @Valid}。
 *
 * <p>★ 本文件**不含任何向量/生成调用**（{@code §ENG-009} 边界 (a)）。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 注册请求。 */
    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 4, max = 32, message = "用户名长度需 4~32")
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需 6~64")
            String password,

            @Size(max = 32, message = "昵称最长 32")
            String nickname,

            @Email(message = "邮箱格式不正确")
            @Size(max = 128)
            String email,

            @Size(max = 64)
            String college,

            @Size(max = 64)
            String major) {
    }

    /** 登录请求。 */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 当前用户视图（**永远不含口令字段**）。 */
    public record UserView(
            Long id,
            String username,
            String nickname,
            String email,
            String college,
            String major,
            Integer status) {
    }

    /** 登录结果：令牌 + 用户视图。 */
    public record LoginResult(String token, String tokenType, UserView user) {
    }
}
