package com.zhikeban.bishe.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `LoginUser` 单测（`ENG-013`）—— 已认证主体只承载"是谁"，**不缓存口令**。
 */
class LoginUserTest {

    @Test
    void exposesUserIdAndUsernameAndOnlyTheUserRole() {
        LoginUser user = new LoginUser(11L, "user-11");

        assertThat(user.getUserId()).isEqualTo(11L);
        assertThat(user.getUsername()).isEqualTo("user-11");
        assertThat(user.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList())
                .containsExactly("ROLE_USER");
    }

    @Test
    void holdsNoPasswordAndIsAlwaysUsable() {
        LoginUser user = new LoginUser(11L, "user-11");

        assertThat(user.getPassword()).isNull();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }
}
