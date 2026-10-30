package com.zhikeban.bishe.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `TokenService` 单测（`ENG-013`）—— 签发 / 反查 / 吊销（进程内记忆表 + 不透明 UUID）。
 * ★ 边界如实记录：进程重启即失效，这不是"分布式会话"。
 */
class TokenServiceTest {

    private final TokenService service = new TokenService();

    @Test
    void issuedTokenIsOpaqueUuidWithoutDashes() {
        String token = service.issue(7L);

        assertThat(token).hasSize(32).doesNotContain("-");
        assertThat(service.resolve(token)).contains(7L);
    }

    @Test
    void everyIssueProducesADistinctToken() {
        String a = service.issue(7L);
        String b = service.issue(7L);

        assertThat(a).isNotEqualTo(b);
        assertThat(service.resolve(a)).contains(7L);
        assertThat(service.resolve(b)).contains(7L);
    }

    @Test
    void unknownOrBlankTokensResolveToEmpty() {
        assertThat(service.resolve(null)).isEmpty();
        assertThat(service.resolve("")).isEmpty();
        assertThat(service.resolve("   ")).isEmpty();
        assertThat(service.resolve("不存在")).isEmpty();
        assertThat(service.resolve("x")).isEqualTo(Optional.empty());
    }

    @Test
    void revokeRemovesTheTokenAndIsHonestAboutWhetherItExisted() {
        String token = service.issue(1L);

        assertThat(service.revoke(token)).isTrue();
        assertThat(service.resolve(token)).isEmpty();
        assertThat(service.revoke(token)).isFalse();
        assertThat(service.revoke(null)).isFalse();
        assertThat(service.revoke("  ")).isFalse();
    }
}
