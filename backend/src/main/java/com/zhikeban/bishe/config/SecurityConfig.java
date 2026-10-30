package com.zhikeban.bishe.config;

import com.zhikeban.bishe.security.TokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置（`ENG-007` 打底，`ENG-009` 任务① 落**最小可用**的令牌过滤链）。
 *
 * <p>★ 变化点（对照 `ENG-007` 的骨架版）：① 取消 {@code httpBasic}，改**无状态 + Bearer 令牌**；
 * ② 放行清单加上注册 / 登录（以及 CORS 预检、错误页兜底），其余仍需认证；③ 挂
 * {@link TokenAuthFilter}。
 *
 * <p>★ 本类同时是 <b>Spring Security 真实引用</b>的落点（`§ENG-007` 的 `A4a` 同口径）。
 * ★ 真正的 RBAC（{@code user_role}/{@code role}）与 JWT + Redis 会话属**后续编码单**，
 * 本单只做到"注册→登录→登出→当前用户"这条链可用。
 *
 * <p>★ 本类**不引用**任何向量/生成类型（{@code §ENG-009} 边界 (a)）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 口令编码器：概设 §4.4 的 {@code user.password} 明写 BCrypt 密文。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenAuthFilter tokenAuthFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/health", "/error").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 开发期跨域：前端 dev server（5173）直连后端（8080）时需要。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
