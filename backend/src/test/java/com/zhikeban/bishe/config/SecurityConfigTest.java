package com.zhikeban.bishe.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `SecurityConfig` 单测（`ENG-013`）—— 口令编码器（BCrypt，概设 §4.4）与开发期 CORS 配置。
 * ★ `securityFilterChain(HttpSecurity)` 需要真容器链路，属集成面，单测不假装覆盖它。
 */
class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void passwordEncoderIsBcryptAndVerifiesRoundTrip() {
        PasswordEncoder encoder = config.passwordEncoder();

        String hash = encoder.encode("secret-123");
        assertThat(hash).startsWith("$2").isNotEqualTo("secret-123");
        assertThat(encoder.matches("secret-123", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void corsAllowsTheFrontendDevServerAndTheUsedHttpMethods() {
        CorsConfigurationSource source = config.corsConfigurationSource();

        CorsConfiguration cfg = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/courses"));

        assertThat(cfg).isNotNull();
        assertThat(cfg.getAllowedOriginPatterns()).containsExactly("*");
        assertThat(cfg.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cfg.getAllowedHeaders()).containsExactly("*");
        assertThat(cfg.getAllowCredentials()).isTrue();
    }
}
