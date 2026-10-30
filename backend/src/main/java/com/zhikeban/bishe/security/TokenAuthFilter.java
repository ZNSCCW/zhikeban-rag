package com.zhikeban.bishe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Bearer 令牌过滤器（任务①）。
 *
 * <p>★ 请求头 {@code Authorization: Bearer <token>} → 反查主体 → 写入
 * {@link SecurityContextHolder}。**令牌无效时不抛异常、不写响应**，直接放行，
 * 由 {@code SecurityConfig} 的授权规则决定是否 401/403（保持过滤器职责单一）。
 *
 * <p>★ 本类**不引用**任何向量/生成类型（{@code §ENG-009} 边界 (a)）。
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public TokenAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            Optional<Long> userId = tokenService.resolve(token);
            if (userId.isPresent()) {
                LoginUser principal = new LoginUser(userId.get(), "user-" + userId.get());
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
