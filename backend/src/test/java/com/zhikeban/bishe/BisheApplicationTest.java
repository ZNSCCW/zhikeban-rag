package com.zhikeban.bishe;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `BisheApplication` 单测（`ENG-013`）—— 启动类契约：`@SpringBootApplication` ＋
 * `@MapperScan("com.zhikeban.bishe.mapper")` ＋ 可被容器调用的 `main`。
 * ★ 刻意**不调用** `main`（那会起整个 Spring 上下文，属集成面）。
 */
class BisheApplicationTest {

    @Test
    void isAspringBootApplicationWithMapperScan() {
        assertThat(BisheApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();

        MapperScan scan = BisheApplication.class.getAnnotation(MapperScan.class);
        assertThat(scan).isNotNull();
        assertThat(scan.value()).containsExactly("com.zhikeban.bishe.mapper");
    }

    @Test
    void exposesAStandardMainEntryPoint() throws Exception {
        Method main = BisheApplication.class.getDeclaredMethod("main", String[].class);

        assertThat(main).isNotNull();
        assertThat(java.lang.reflect.Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(java.lang.reflect.Modifier.isPublic(main.getModifiers())).isTrue();
    }
}
