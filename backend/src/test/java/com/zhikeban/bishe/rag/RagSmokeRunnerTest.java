package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.vector.EmbeddingClient;
import com.zhikeban.bishe.vector.VectorStore;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `RagSmokeRunner` 单测（`ENG-013`）—— 只测**可离线的纯逻辑**：
 * 构造、机器可读标记、正文摘录（`head`）、单值查询（`queryLong`）与阶段追加（`add`）。
 *
 * <p>★ `run()` 会 `System.exit`（它是"冒烟执行体"，由 `.tools/eng012-smoke.mjs` 用
 * `java -jar … --zhikeban.smoke.enabled=true` 拉起）⇒ **刻意不在单测里调用**：
 * 端到端真跑的判定归 `§ENG-012` 的冒烟脚本，不归本单。
 */
class RagSmokeRunnerTest {

    private RagSmokeRunner newRunner() {
        return new RagSmokeRunner(mock(DataSource.class), mock(VectorStore.class), mock(EmbeddingClient.class),
                mock(RagOrchestrator.class), mock(PermissionFilter.class), 18080);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = RagSmokeRunner.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> stages(RagSmokeRunner runner) throws Exception {
        Field f = RagSmokeRunner.class.getDeclaredField("stages");
        f.setAccessible(true);
        return (List<Map<String, Object>>) f.get(runner);
    }

    @Test
    void markerConstantIsTheContractWithTheSmokeScript() {
        assertThat(RagSmokeRunner.MARKER).isEqualTo("@@ENG012_STAGES@@");
    }

    @Test
    void constructionWiresAllCollaborators() {
        assertThat(newRunner()).isNotNull();
    }

    @Test
    void headCollapsesWhitespaceAndTruncatesTo160Chars() throws Exception {
        RagSmokeRunner runner = newRunner();

        assertThat(invoke(runner, "head", new Class<?>[]{String.class}, (Object) null)).isEqualTo("");
        assertThat(invoke(runner, "head", new Class<?>[]{String.class}, "  多   空白\n换行 "))
                .isEqualTo("多 空白 换行");

        String head = (String) invoke(runner, "head", new Class<?>[]{String.class}, "字".repeat(200));
        assertThat(head).hasSize(161);
        assertThat(head).endsWith("…");
    }

    @Test
    void stagesAreAppendedWithTheAgreedFieldNames() throws Exception {
        RagSmokeRunner runner = newRunner();

        invoke(runner, "add", new Class<?>[]{String.class, String.class, boolean.class, String.class},
                "标注", "STAGE", true, null);
        invoke(runner, "add", new Class<?>[]{String.class, String.class, boolean.class, String.class},
                "标注2", "STAGE", false, "细节");

        List<Map<String, Object>> stages = stages(runner);
        assertThat(stages).hasSize(2);
        assertThat(stages.get(0)).containsEntry("label", "标注").containsEntry("stage", "STAGE")
                .containsEntry("ok", true).containsEntry("detail", "").containsKey("ms");
        assertThat(stages.get(1)).containsEntry("ok", false).containsEntry("detail", "细节");
    }

    @Test
    void queryLongReturnsValueOrNull() throws Exception {
        RagSmokeRunner runner = newRunner();
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong(1)).thenReturn(88L);

        Object found = invoke(runner, "queryLong", new Class<?>[]{Connection.class, String.class, Object[].class},
                conn, "SELECT id FROM t WHERE a = ?", new Object[]{"x"});
        assertThat(found).isEqualTo(88L);

        when(rs.next()).thenReturn(false);
        Object missing = invoke(runner, "queryLong", new Class<?>[]{Connection.class, String.class, Object[].class},
                conn, "SELECT id FROM t", new Object[0]);
        assertThat(missing).isNull();
    }
}
