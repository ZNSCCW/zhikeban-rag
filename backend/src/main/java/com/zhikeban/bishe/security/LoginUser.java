package com.zhikeban.bishe.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 已认证主体（任务①）。
 *
 * <p>★ 只承载"是谁"（id + 登录名），**不缓存口令**：口令校验在登录那一刻完成，
 * 之后的请求只凭令牌映射回来的主体。
 *
 * <p>★ 本类**不引用**任何向量/生成类型（{@code §ENG-009} 边界 (a)）。
 */
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;

    public LoginUser(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** ★ 不持有口令：认证已完成，本字段只用于满足 {@link UserDetails} 契约。 */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
