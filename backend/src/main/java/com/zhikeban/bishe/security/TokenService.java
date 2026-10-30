package com.zhikeban.bishe.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量令牌服务（任务①：登录签发、登出吊销、请求鉴权反查）。
 *
 * <p>★★ 设计口径（**如实交代，不夸大**）：本单是 **MVP 一 的最小闭环**，令牌采用
 * **进程内记忆表**（{@link ConcurrentHashMap}）+ 不透明 UUID，**不是 JWT、也不落 Redis**。
 * 取舍理由：① 概设 §3.4 的 JWT + Redis 口径属**后续编码单**；② 本单只要求"注册→登录→
 * 登出→当前用户"这条链走得通；③ 不引新依赖（`pom.xml` 不动，避免与既有 `A1`/编译断言打架）。
 * ★ 进程重启后令牌失效 —— 这是**已知边界**，不写成"已实现分布式会话"。
 *
 * <p>★ 本类**不引用**任何向量/生成类型（{@code §ENG-009} 边界 (a)）。
 */
@Service
public class TokenService {

    /** token → userId。 */
    private final Map<String, Long> tokenToUser = new ConcurrentHashMap<>();

    /** 签发新令牌。 */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUser.put(token, userId);
        return token;
    }

    /** 反查令牌对应的用户 id（无则空）。 */
    public Optional<Long> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokenToUser.get(token));
    }

    /** 登出：吊销令牌。返回是否真的吊销了一个存在的令牌。 */
    public boolean revoke(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenToUser.remove(token) != null;
    }
}
