package com.zhikeban.bishe.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `MysqlVectorStoreImpl` 单测（`ENG-013`）—— 默认路径（`TB-28`）：
 * `float32` 小端序列化、**维度校验**、全量扫描 + **应用层余弦**、TopK、重建与清理。
 */
class MysqlVectorStoreImplTest {

    private DataSource dataSource;
    private Connection conn;
    private PreparedStatement ps;
    private ResultSet rs;
    private EmbeddingClient embeddingClient;
    private MysqlVectorStoreImpl store;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        conn = mock(Connection.class);
        ps = mock(PreparedStatement.class);
        rs = mock(ResultSet.class);
        embeddingClient = mock(EmbeddingClient.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        store = new MysqlVectorStoreImpl(dataSource, embeddingClient);
    }

    private void stubRow(long chunkId, long courseId, float[] embedding, String content) throws Exception {
        when(rs.getInt("dim")).thenReturn(embedding.length);
        when(rs.getBytes("embedding")).thenReturn(MysqlVectorStoreImpl.toBytes(embedding));
        when(rs.getLong("chunk_id")).thenReturn(chunkId);
        when(rs.getLong("course_id")).thenReturn(courseId);
        when(rs.getLong("material_id")).thenReturn(10L);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getString("content")).thenReturn(content);
        when(rs.getString("metadata")).thenReturn("{\"chapter\":\"第1章\"}");
    }

    @Test
    void providerIsMysql() {
        assertThat(store.provider()).isEqualTo("mysql");
    }

    @Test
    void upsertBatchesOnlyUsableRecordsAndSumsAffectedRows() throws Exception {
        when(ps.executeBatch()).thenReturn(new int[]{1, 2});

        int affected = store.upsert(List.of(
                new VectorRecord(1L, 2L, 2, new float[]{1f, 0f}, "{\"a\":1}"),
                new VectorRecord(null, 2L, 2, new float[]{1f, 0f}, "{\"a\":1}"),
                new VectorRecord(3L, null, 2, new float[]{0f, 1f}, null)));

        assertThat(affected).isEqualTo(3);
        verify(ps, times(2)).addBatch();
        verify(ps).setLong(1, 1L);
        verify(ps).setLong(2, 2L);
        verify(ps).setBytes(3, MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}));
        verify(ps, times(2)).setInt(4, 2);
        verify(ps).setString(5, "{\"a\":1}");
        // courseId 为 null 时写 0（不写 NULL）
        verify(ps).setLong(2, 0L);
    }

    @Test
    void upsertReturnsZeroForEmptyInputWithoutTouchingTheDatabase() {
        assertThat(store.upsert(null)).isZero();
        assertThat(store.upsert(List.of())).isZero();
    }

    @Test
    void upsertWrapsSqlFailure() throws Exception {
        when(ps.executeBatch()).thenThrow(new SQLException("duplicate"));

        assertThatThrownBy(() -> store.upsert(List.of(new VectorRecord(1L, 2L, 1, new float[]{1f}, null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL 向量写入失败");
    }

    @Test
    void deleteBuildsInClauseAndReturnsAffected() throws Exception {
        when(ps.executeUpdate()).thenReturn(2);

        assertThat(store.delete(List.of(5L, 6L))).isEqualTo(2);
        verify(ps).setLong(1, 5L);
        verify(ps).setLong(2, 6L);
        assertThat(store.delete(null)).isZero();
        assertThat(store.delete(List.of())).isZero();
    }

    @Test
    void deleteWrapsSqlFailure() throws Exception {
        when(ps.executeUpdate()).thenThrow(new SQLException("locked"));

        assertThatThrownBy(() -> store.delete(List.of(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL 向量删除失败");
    }

    @Test
    void searchComputesCosineAndSortsDescending() throws Exception {
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getInt("dim")).thenReturn(2);
        when(rs.getBytes("embedding")).thenReturn(
                MysqlVectorStoreImpl.toBytes(new float[]{0f, 1f}),
                MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}));
        when(rs.getLong("chunk_id")).thenReturn(1L, 2L);
        when(rs.getLong("course_id")).thenReturn(2L);
        when(rs.getLong("material_id")).thenReturn(10L);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getString("content")).thenReturn("正交", "同向");
        when(rs.getString("metadata")).thenReturn("{\"chapter\":\"第1章\"}");

        List<SearchHit> hits = store.search(new SearchQuery("余弦", new float[]{1f, 0f}, List.of(2L), 5));

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).chunkId()).isEqualTo(2L);
        assertThat(hits.get(0).score()).isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(hits.get(1).score()).isCloseTo(0.0d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(hits.get(0).content()).isEqualTo("同向");
        assertThat(hits.get(0).metadata()).isEqualTo("{\"chapter\":\"第1章\"}");
    }

    @Test
    void searchHonoursTopK() throws Exception {
        when(rs.next()).thenReturn(true, true, true, false);
        when(rs.getInt("dim")).thenReturn(2);
        when(rs.getBytes("embedding")).thenReturn(
                MysqlVectorStoreImpl.toBytes(new float[]{0f, 1f}),
                MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}),
                MysqlVectorStoreImpl.toBytes(new float[]{0.5f, 0.5f}));
        when(rs.getLong("chunk_id")).thenReturn(1L, 2L, 3L);
        when(rs.getLong("course_id")).thenReturn(2L);
        when(rs.getLong("material_id")).thenReturn(10L);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getString("content")).thenReturn("a", "b", "c");
        when(rs.getString("metadata")).thenReturn(null);

        List<SearchHit> hits = store.search(new SearchQuery("q", new float[]{1f, 0f}, List.of(2L), 1));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo(2L);
    }

    @Test
    void searchRejectsDimensionMismatchInsteadOfScoringNonsense() throws Exception {
        when(rs.next()).thenReturn(true);
        when(rs.getInt("dim")).thenReturn(3);

        assertThatThrownBy(() -> store.search(new SearchQuery("q", new float[]{1f, 0f}, List.of(2L), 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("向量维度不一致");
    }

    @Test
    void searchSkipsZeroNormRowsAndMissingMaterialIds() throws Exception {
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getInt("dim")).thenReturn(2);
        when(rs.getBytes("embedding")).thenReturn(
                MysqlVectorStoreImpl.toBytes(new float[]{0f, 0f}),
                MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}));
        when(rs.getLong("chunk_id")).thenReturn(8L);   // ★ 被跳过的行不会消费本 stub（只有入选行才取 chunk_id）
        when(rs.getLong("course_id")).thenReturn(2L);
        when(rs.getLong("material_id")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getString("content")).thenReturn("零向量", "正常");
        when(rs.getString("metadata")).thenReturn(null);

        List<SearchHit> hits = store.search(new SearchQuery("q", new float[]{1f, 0f}, List.of(2L), 5));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo(8L);
        assertThat(hits.get(0).materialId()).isZero();
    }

    @Test
    void searchFallsBackToEmbeddingClientAndHandlesEmptyVector() throws Exception {
        when(embeddingClient.embed("q")).thenReturn(new float[0]);

        assertThat(store.search(new SearchQuery("q", null, List.of(2L), 5))).isEmpty();
        verify(embeddingClient).embed("q");
    }

    @Test
    void searchWrapsSqlFailure() throws Exception {
        when(ps.executeQuery()).thenThrow(new SQLException("connection reset"));

        assertThatThrownBy(() -> store.search(new SearchQuery("q", new float[]{1f}, List.of(2L), 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL 向量检索失败");
    }

    @Test
    void rebuildReadsChunksEmbedsThenReplacesRows() throws Exception {
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("chunk_id")).thenReturn(5L);
        when(rs.getString("content")).thenReturn("切片正文");
        when(rs.getString("chapter")).thenReturn("第1章");
        when(rs.getInt("page_no")).thenReturn(3);
        when(rs.getString("material_name")).thenReturn("资料A");
        when(rs.getLong("course_id")).thenReturn(2L);
        when(embeddingClient.embed("切片正文")).thenReturn(new float[]{1f, 0f});
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.executeBatch()).thenReturn(new int[]{1});

        RebuildReport report = store.rebuild(2L);

        assertThat(report.courseId()).isEqualTo(2L);
        assertThat(report.processed()).isEqualTo(1);
        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.provider()).isEqualTo("mysql");
        assertThat(report.elapsedMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void countByCourseReturnsCountAndMinusOneOnFailure() throws Exception {
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(7);
        assertThat(store.countByCourse(2L)).isEqualTo(7);

        when(ps.executeQuery()).thenThrow(new SQLException("gone"));
        assertThat(store.countByCourse(2L)).isEqualTo(-1);
    }

    @Test
    void float32LittleEndianSerialisationRoundTrips() {
        float[] v = {1.5f, -2.25f, 0f};
        byte[] bytes = MysqlVectorStoreImpl.toBytes(v);

        assertThat(bytes).hasSize(v.length * Float.BYTES);
        assertThat(MysqlVectorStoreImpl.toFloats(bytes)).containsExactly(v);
        assertThat(MysqlVectorStoreImpl.toBytes(null)).isEmpty();
        assertThat(MysqlVectorStoreImpl.toFloats(null)).isEmpty();
        assertThat(MysqlVectorStoreImpl.toFloats(new byte[2])).isEmpty();
    }
}
