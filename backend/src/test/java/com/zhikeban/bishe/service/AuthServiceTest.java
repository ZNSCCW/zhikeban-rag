package com.zhikeban.bishe.service;

import com.zhikeban.bishe.domain.entity.User;
import com.zhikeban.bishe.mapper.UserMapper;
import com.zhikeban.bishe.security.TokenService;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `AuthService` 单测（`ENG-013`）—— 注册 / 登录 / 登出 / 当前用户：
 * 口令 **BCrypt 密文**入库、登录失败**不区分**"用户不存在"与"口令错"（防账号枚举）、
 * **任何响应都不带 password 字段**。
 */
class AuthServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder encoder;
    private TokenService tokenService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        encoder = mock(PasswordEncoder.class);
        tokenService = new TokenService();
        service = new AuthService(userMapper, encoder, tokenService);
    }

    private static User storedUser(long id, String username, String hash, Integer status) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(hash);
        u.setNickname("昵称");
        u.setEmail("u@example.com");
        u.setCollege("软件学院");
        u.setMajor("软件工程");
        u.setStatus(status);
        return u;
    }

    @Test
    void registerRejectsDuplicatedUsernameWith409() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.register(new AuthDtos.RegisterRequest(
                "alice", "pw123456", null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void registerHashesPasswordAndFillsDefaults() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode("pw123456")).thenReturn("$2a$hash");

        AuthDtos.UserView view = service.register(new AuthDtos.RegisterRequest(
                "alice", "pw123456", "  ", "a@example.com", "软件学院", "软件工程"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPassword()).isEqualTo("$2a$hash");
        assertThat(saved.getNickname()).isEqualTo("alice");     // 空白昵称退化为用户名
        assertThat(saved.getEmail()).isEqualTo("a@example.com");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getDeleted()).isZero();

        assertThat(view.username()).isEqualTo("alice");
        assertThat(view.nickname()).isEqualTo("alice");
    }

    @Test
    void registerKeepsAnExplicitNickname() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode("pw123456")).thenReturn("$2a$hash");

        service.register(new AuthDtos.RegisterRequest("alice", "pw123456", "小艾", null, null, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("小艾");
    }

    @Test
    void loginHidesWhetherTheUserExistsOrThePasswordWasWrong() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("ghost", "pw123456")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));

        User user = storedUser(1L, "alice", "$2a$hash", 1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(encoder.matches("bad", "$2a$hash")).thenReturn(false);
        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("alice", "bad")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void loginRejectsDisabledAccountWith403() {
        when(userMapper.selectOne(any())).thenReturn(storedUser(1L, "alice", "$2a$hash", 0));
        when(encoder.matches("pw123456", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("alice", "pw123456")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
    }

    @Test
    void loginIssuesATokenThatTheTokenServiceCanResolve() {
        when(userMapper.selectOne(any())).thenReturn(storedUser(1L, "alice", "$2a$hash", 1));
        when(encoder.matches("pw123456", "$2a$hash")).thenReturn(true);

        AuthDtos.LoginResult result = service.login(new AuthDtos.LoginRequest("alice", "pw123456"));

        assertThat(result.token()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.user().id()).isEqualTo(1L);
        assertThat(tokenService.resolve(result.token())).contains(1L);
    }

    @Test
    void userViewNeverCarriesAPasswordField() {
        assertThat(Arrays.stream(AuthDtos.UserView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("password");
    }

    @Test
    void logoutRevokesTheToken() {
        String token = tokenService.issue(1L);

        service.logout(token);

        assertThat(tokenService.resolve(token)).isEmpty();
    }

    @Test
    void currentUserNotFoundBecomes404() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.currentUser(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void currentUserReturnsTheView() {
        when(userMapper.selectById(1L)).thenReturn(storedUser(1L, "alice", "$2a$hash", 1));

        AuthDtos.UserView view = service.currentUser(1L);

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.username()).isEqualTo("alice");
        assertThat(view.email()).isEqualTo("u@example.com");
    }
}
