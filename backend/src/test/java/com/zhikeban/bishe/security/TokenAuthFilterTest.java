package com.zhikeban.bishe.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `TokenAuthFilter` 单测（`ENG-013`）—— `Authorization: Bearer <token>` → 反查主体 → 写入
 * `SecurityContextHolder`；**令牌无效时既不抛异常也不写响应**（授权交由 SecurityConfig 的规则决定）。
 */
class TokenAuthFilterTest {

    private final TokenService tokenService = new TokenService();
    private final TokenAuthFilter filter = new TokenAuthFilter(tokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain run(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses");
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void validBearerTokenPopulatesTheSecurityContext() throws Exception {
        String token = tokenService.issue(42L);

        MockFilterChain chain = run("Bearer " + token);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(LoginUser.class);
        assertThat(((LoginUser) auth.getPrincipal()).getUserId()).isEqualTo(42L);
        assertThat(auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList())
                .containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isNotNull();      // 过滤器只放行、不短路
    }

    @Test
    void tokenWithSurroundingSpacesIsTrimmed() throws Exception {
        String token = tokenService.issue(5L);

        run("Bearer   " + token + "  ");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void missingHeaderLeavesTheContextEmptyButStillContinuesTheChain() throws Exception {
        MockFilterChain chain = run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void unknownTokenOrWrongSchemeIsIgnored() throws Exception {
        run("Bearer not-a-real-token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        run("Token " + tokenService.issue(1L));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        run("Bearer ");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void anAlreadyAuthenticatedRequestIsNotOverwritten() throws Exception {
        LoginUser existing = new LoginUser(9L, "user-9");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(existing, null, existing.getAuthorities()));

        run("Bearer " + tokenService.issue(1L));

        assertThat(((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .getUserId()).isEqualTo(9L);
    }
}
