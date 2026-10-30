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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `OracleVectorStoreImpl` 单测（`ENG-013`）—— Oracle 主实现（`TB-27`）：
 * 原生 `VECTOR_DISTANCE(..., COSINE)` 优先，**失败即退化为无索引精确检索**（`§ENG-012` 的 `B-a` 边界：
 * `HNSW` 建没建成都**不判失败**，度量仍是余弦、接口不变）。
 */
class OracleVectorStoreImplTest {

    private DataSource dataSource;
    private Connection conn;
    private PreparedStatement ps;
    private ResultSet rs;
    private EmbeddingClient embeddingClient;
    private OracleVectorStoreImpl store;

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
        store = new OracleVectorStoreImpl(dataSource, embeddingClient);
    }

    @Test
    void providerIsOracleAndStartsNotDegraded() {
        assertThat(store.provider()).isEqualTo("oracle");
        assertThat(store.isDegraded()).isFalse();
    }

    @Test
    void nativeSearchMapsCosineDistanceToSimilarity() throws Exception {
        when(rs.next()).thenReturn(true, false);
        when(rs.getDouble("distance")).thenReturn(0.25d);
        when(rs.getLong("chunk_id")).thenReturn(1L);
        when(rs.getLong("course_id")).thenReturn(2L);

        List<SearchHit> hits = store.search(new SearchQuery("q", new float[]{1f, 0f}, List.of(2L), 5));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).score()).isCloseTo(0.75d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(store.isDegraded()).isFalse();
        // 原生路径把查询向量按 float32 绑定进 VECTOR 参数
        verify(ps).setBytes(1, MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}));
    }

    @Test
    void nativeFailureDegradesToIndexlessExactSearch() throws Exception {
        PreparedStatement nativePs = mock(PreparedStatement.class);
        PreparedStatement scanPs = mock(PreparedStatement.class);
        ResultSet scanRs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(nativePs, scanPs);
        when(nativePs.executeQuery()).thenThrow(new SQLException("HNSW 索引未建成"));
        when(scanPs.executeQuery()).thenReturn(scanRs);
        when(scanRs.next()).thenReturn(true, false);
        when(scanRs.getBytes("embedding")).thenReturn(MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}));
        when(scanRs.getLong("chunk_id")).thenReturn(9L);
        when(scanRs.getLong("course_id")).thenReturn(2L);

        List<SearchHit> hits = store.search(new SearchQuery("q", new float[]{1f, 0f}, List.of(2L), 5));

        assertThat(store.isDegraded()).isTrue();
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo(9L);
        assertThat(hits.get(0).score()).isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void degradedScanSkipsWrongDimensionAndZeroNormRowsAndSortsDescending() throws Exception {
        PreparedStatement nativePs = mock(PreparedStatement.class);
        PreparedStatement scanPs = mock(PreparedStatement.class);
        ResultSet scanRs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(nativePs, scanPs);
        when(nativePs.executeQuery()).thenThrow(new IllegalStateException("矢量绑定失败"));
        when(scanPs.executeQuery()).thenReturn(scanRs);
        when(scanRs.next()).thenReturn(true, true, true, true, false);
        when(scanRs.getBytes("embedding")).thenReturn(
                MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f, 0f}),   // 维度不符 ⇒ 跳过
                MysqlVectorStoreImpl.toBytes(new float[]{0f, 0f}),       // 零向量 ⇒ 跳过
                MysqlVectorStoreImpl.toBytes(new float[]{0.5f, 0.5f}),
                MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}));
        // ★ 被跳过的两行**不会**消费本 stub（只有入选行才取 chunk_id）⇒ 入选的两行拿到 3L、4L
        when(scanRs.getLong("chunk_id")).thenReturn(3L, 4L);
        when(scanRs.getLong("course_id")).thenReturn(2L);

        List<SearchHit> hits = store.search(new SearchQuery("q", new float[]{1f, 0f}, List.of(2L), 5));

        assertThat(hits).extracting(SearchHit::chunkId).containsExactly(4L, 3L);
        assertThat(hits.get(0).score()).isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(hits.get(1).score()).isCloseTo(Math.sqrt(0.5d), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void degradedScanHonoursTopK() throws Exception {
        PreparedStatement nativePs = mock(PreparedStatement.class);
        PreparedStatement scanPs = mock(PreparedStatement.class);
        ResultSet scanRs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(nativePs, scanPs);
        when(nativePs.executeQuery()).thenThrow(new SQLException("down"));
        when(scanPs.executeQuery()).thenReturn(scanRs);
        when(scanRs.next()).thenReturn(true, true, false);
        when(scanRs.getBytes("embedding")).thenReturn(
                MysqlVectorStoreImpl.toBytes(new float[]{0f, 1f}),
                MysqlVectorStoreImpl.toBytes(new float[]{1f, 0f}));
        when(scanRs.getLong("chunk_id")).thenReturn(1L, 2L);
        when(scanRs.getLong("course_id")).thenReturn(2L);

        List<SearchHit> hits = store.search(new SearchQuery("q", new float[]{1f, 0f}, List.of(2L), 1));

        assertThat(hits).extracting(SearchHit::chunkId).containsExactly(2L);
    }

    @Test
    void searchUsesEmbeddingClientAndReturnsEmptyForBlankVector() throws Exception {
        when(embeddingClient.embed("q")).thenReturn(new float[0]);

        assertThat(store.search(new SearchQuery("q", null, List.of(2L), 5))).isEmpty();
        verify(embeddingClient).embed("q");
    }

    @Test
    void searchAlsoDegradesWhenTheScanFailureIsFatal() throws Exception {
        PreparedStatement nativePs = mock(PreparedStatement.class);
        PreparedStatement scanPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(nativePs, scanPs);
        when(nativePs.executeQuery()).thenThrow(new SQLException("down"));
        when(scanPs.executeQuery()).thenThrow(new SQLException("scan down"));

        assertThatThrownBy(() -> store.search(new SearchQuery("q", new float[]{1f}, List.of(2L), 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Oracle 退化精确检索也失败");
    }

    @Test
    void upsertAndDeleteBehaveLikeTheMysqlSide() throws Exception {
        when(ps.executeBatch()).thenReturn(new int[]{1});
        assertThat(store.upsert(List.of(new VectorRecord(1L, 2L, 2, new float[]{1f, 0f}, null)))).isEqualTo(1);
        assertThat(store.upsert(null)).isZero();

        when(ps.executeUpdate()).thenReturn(1);
        assertThat(store.delete(List.of(1L))).isEqualTo(1);
        assertThat(store.delete(List.of())).isZero();
    }

    @Test
    void upsertAndDeleteWrapSqlFailures() throws Exception {
        when(ps.executeBatch()).thenThrow(new SQLException("merge failed"));
        assertThatThrownBy(() -> store.upsert(List.of(new VectorRecord(1L, 2L, 1, new float[]{1f}, null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Oracle 向量写入失败");

        when(ps.executeUpdate()).thenThrow(new SQLException("delete failed"));
        assertThatThrownBy(() -> store.delete(List.of(1L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Oracle 向量删除失败");
    }

    @Test
    void rebuildEmbedsChunksThenReplacesRows() throws Exception {
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("chunk_id")).thenReturn(5L);
        when(rs.getString("content")).thenReturn("切片正文");
        when(embeddingClient.embed("切片正文")).thenReturn(new float[]{1f, 0f});
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.executeBatch()).thenReturn(new int[]{1});

        RebuildReport report = store.rebuild(2L);

        assertThat(report.processed()).isEqualTo(1);
        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.provider()).isEqualTo("oracle");
    }

    @Test
    void rebuildWrapsReadFailure() throws Exception {
        when(ps.executeQuery()).thenThrow(new SQLException("no table"));

        assertThatThrownBy(() -> store.rebuild(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Oracle 重建失败");
    }
}
