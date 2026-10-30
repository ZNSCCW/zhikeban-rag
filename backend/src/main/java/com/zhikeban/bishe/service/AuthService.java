package com.zhikeban.bishe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikeban.bishe.domain.entity.User;
import com.zhikeban.bishe.mapper.UserMapper;
import com.zhikeban.bishe.security.TokenService;
import com.zhikeban.bishe.web.ApiException;
import com.zhikeban.bishe.web.dto.AuthDtos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证 / 用户服务（任务①；`SRS` `FR-1` 子集：注册、登录、登出、当前用户）。
 *
 * <p>★ 口令一律 **BCrypt 密文**入库（概设 §4.4 的 {@code user.password} 明写），
 * 且**任何响应都不带 {@code password} 字段**（{@link AuthDtos.UserView} 里没有这一项）。
 *
 * <p>★ 本类**不引用**任何向量/生成类型（{@code §ENG-009} 边界 (a)）。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** 注册：用户名唯一，口令 BCrypt 入库。 */
    public AuthDtos.UserView register(AuthDtos.RegisterRequest req) {
        Long dup = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, req.username()));
        if (dup != null && dup > 0) {
            throw ApiException.conflict("用户名已被占用：" + req.username());
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setNickname(req.nickname() == null || req.nickname().isBlank() ? req.username() : req.nickname());
        u.setEmail(req.email());
        u.setCollege(req.college());
        u.setMajor(req.major());
        u.setStatus(1);
        u.setDeleted(0);
        userMapper.insert(u);
        return toView(u);
    }

    /** 登录：校验口令 → 签发令牌。 */
    public AuthDtos.LoginResult login(AuthDtos.LoginRequest req) {
        User u = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, req.username()));
        if (u == null || u.getPassword() == null || !passwordEncoder.matches(req.password(), u.getPassword())) {
            // ★ 不区分"用户不存在"与"口令错"，避免账号枚举
            throw ApiException.unauthorized("用户名或密码不正确");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw ApiException.forbidden("账号已被禁用");
        }
        return new AuthDtos.LoginResult(tokenService.issue(u.getId()), "Bearer", toView(u));
    }

    /** 登出：吊销令牌（幂等 —— 令牌本就无效也返回成功，不泄露令牌是否存在）。 */
    public void logout(String token) {
        tokenService.revoke(token);
    }

    /** 当前用户。 */
    public AuthDtos.UserView currentUser(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw ApiException.notFound("用户不存在：" + userId);
        }
        return toView(u);
    }

    /** 实体 → 视图（**不含口令**）。 */
    private AuthDtos.UserView toView(User u) {
        return new AuthDtos.UserView(u.getId(), u.getUsername(), u.getNickname(),
                u.getEmail(), u.getCollege(), u.getMajor(), u.getStatus());
    }
}
