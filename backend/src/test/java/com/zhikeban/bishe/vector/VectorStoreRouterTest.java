package com.zhikeban.bishe.vector;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `VectorStoreRouter` 单测（`ENG-013`）—— "**双实现 + 一个接缝**"（`NFR-REL-08`）：
 * 按 `VECTOR_STORE` 裁决激活实现、缺 Bean 时退到另一个、运行时可**降级到 MySQL 兜底**，
 * 业务侧始终只注入一个 `VectorStore`。
 */
class VectorStoreRouterTest {

    private Environment env;
    private ApplicationContext ctx;
    private VectorStore mysql;
    private VectorStore oracle;

    private VectorStoreRouter routerWith(String mode) {
        env = mock(Environment.class);
        ctx = mock(ApplicationContext.class);
        mysql = mock(VectorStore.class);
        oracle = mock(VectorStore.class);
        when(env.getProperty("VECTOR_STORE", "mysql")).thenReturn(mode);
        when(ctx.getBean(VectorStoreRouter.MYSQL_BEAN, VectorStore.class)).thenReturn(mysql);
        when(ctx.getBean(VectorStoreRouter.ORACLE_BEAN, VectorStore.class)).thenReturn(oracle);
        when(mysql.provider()).thenReturn("mysql");
        when(oracle.provider()).thenReturn("oracle");
        return new VectorStoreRouter(env, ctx);
    }

    @Test
    void defaultsToMysqlWhenModeIsNotOracle() {
        VectorStoreRouter router = routerWith("mysql");

        assertThat(VectorStoreRouter.MYSQL_BEAN).isEqualTo("mysqlVectorStore");
        assertThat(VectorStoreRouter.ORACLE_BEAN).isEqualTo("oracleVectorStore");
        assertThat(router.provider()).isEqualTo("mysql");
    }

    @Test
    void selectsOracleCaseInsensitively() {
        assertThat(routerWith("Oracle").provider()).isEqualTo("oracle");
        assertThat(routerWith("ORACLE").provider()).isEqualTo("oracle");
    }

    @Test
    void delegatesEveryVectorStoreOperationToTheActiveImplementation() {
        VectorStoreRouter router = routerWith("mysql");
        SearchQuery query = new SearchQuery("q", new float[]{1f}, List.of(2L), 5);
        List<VectorRecord> records = List.of(new VectorRecord(1L, 2L, 1, new float[]{1f}, null));
        List<SearchHit> hits = List.of(new SearchHit(1L, 2L, 3L, 1.0d, "c", null));
        RebuildReport report = new RebuildReport(2L, 1, 0, 5L, "mysql");
        when(mysql.upsert(records)).thenReturn(1);
        when(mysql.delete(List.of(1L))).thenReturn(1);
        when(mysql.search(query)).thenReturn(hits);
        when(mysql.rebuild(2L)).thenReturn(report);

        assertThat(router.upsert(records)).isEqualTo(1);
        assertThat(router.delete(List.of(1L))).isEqualTo(1);
        assertThat(router.search(query)).isSameAs(hits);
        assertThat(router.rebuild(2L)).isSameAs(report);
        verify(mysql).upsert(records);
    }

    @Test
    void fallsBackToTheOtherBeanWhenTheSelectedProfileIsNotActive() {
        // VECTOR_STORE=oracle 但 vector-oracle profile 没激活（Bean 不在容器里）⇒ 退到 mysql 兜底
        env = mock(Environment.class);
        ctx = mock(ApplicationContext.class);
        mysql = mock(VectorStore.class);
        when(env.getProperty("VECTOR_STORE", "mysql")).thenReturn("oracle");
        when(ctx.getBean(VectorStoreRouter.ORACLE_BEAN, VectorStore.class))
                .thenThrow(new IllegalStateException("no bean"));
        when(ctx.getBean(VectorStoreRouter.MYSQL_BEAN, VectorStore.class)).thenReturn(mysql);
        when(mysql.provider()).thenReturn("mysql");

        VectorStoreRouter router = new VectorStoreRouter(env, ctx);

        assertThat(router.provider()).isEqualTo("mysql");
    }

    @Test
    void refusesToStartWhenNeitherImplementationIsPresent() {
        env = mock(Environment.class);
        ctx = mock(ApplicationContext.class);
        when(env.getProperty("VECTOR_STORE", "mysql")).thenReturn("mysql");
        when(ctx.getBean(anyString(), eq(VectorStore.class))).thenThrow(new IllegalStateException("no bean"));

        assertThatThrownBy(() -> new VectorStoreRouter(env, ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VectorStore 双实现都不在容器里");
    }

    @Test
    void runtimeFallbackSwitchesToMysqlWithoutChangingBusinessCode() {
        VectorStoreRouter router = routerWith("oracle");
        assertThat(router.provider()).isEqualTo("oracle");

        router.fallbackToMysql("Oracle 实例不可用");

        assertThat(router.provider()).isEqualTo("mysql");
        verify(ctx).getBean(VectorStoreRouter.MYSQL_BEAN, VectorStore.class);
    }
}
