package com.zhikeban.bishe.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 向量存储 · **Oracle 主实现**（`ENG-012` 任务②；走 `TB-27` `material_chunk_vector`，Oracle Database 23ai）。
 *
 * <p>★ 口径（详设 §4.3.9）：原生向量索引 `HNSW` + 距离度量 `COSINE`；`course_id` 索引列预过滤后取 TopK；
 * 分数语义统一换算为"**相似度，越大越相关**"后返回 —— 与 MySQL 兜底侧的应用层余弦**度量一致**。
 *
 * <p>★★ **可退化（本批写死的边界）**：`Oracle Free` 版的 `HNSW` 索引**可能建不成**。
 * 本实现**不假设索引存在**：一旦原生向量检索抛错（索引缺失 / 实例不可用 / 维度不符），
 * 就 {@code catch} 住并**退化为无索引精确检索**（`scanWithoutIndex`：全量扫描 + 应用层余弦），
 * **度量仍是余弦、接口不变、两张表结构不变**。⇒ `HNSW` 建没建成都**不改变**任何一条验收判据。
 *
 * <p>★ 本类**只做课程级预过滤**；成员级权限过滤是 `PermissionFilter` 的职责。
 */
@Service("oracleVectorStore")
@Profile("vector-oracle")
public class OracleVectorStoreImpl implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(OracleVectorStoreImpl.class);

    private final DataSource dataSource;
    private final EmbeddingClient embeddingClient;

    /** 上一次检索是否走了"无索引精确检索"退化路径（自检/埋点用）。 */
    private volatile boolean degraded;

    public OracleVectorStoreImpl(DataSource dataSource, EmbeddingClient embeddingClient) {
        this.dataSource = dataSource;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public int upsert(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        String sql = "MERGE INTO material_chunk_vector t "
                + "USING (SELECT ? AS chunk_id, ? AS course_id, ? AS embedding FROM dual) s "
                + "ON (t.chunk_id = s.chunk_id) "
                + "WHEN MATCHED THEN UPDATE SET t.course_id = s.course_id, t.embedding = s.embedding "
                + "WHEN NOT MATCHED THEN INSERT (chunk_id, course_id, embedding) "
                + "VALUES (s.chunk_id, s.course_id, s.embedding)";
        int affected = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (VectorRecord r : records) {
                if (r == null || r.chunkId() == null) {
                    continue;
                }
                ps.setLong(1, r.chunkId());
                ps.setLong(2, r.courseId() == null ? 0L : r.courseId());
                ps.setBytes(3, MysqlVectorStoreImpl.toBytes(r.embedding()));
                ps.addBatch();
            }
            for (int n : ps.executeBatch()) {
                affected += Math.max(n, 0);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Oracle 向量写入失败：" + e.getMessage(), e);
        }
        return affected;
    }

    @Override
    public int delete(Collection<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>(chunkIds);
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        String sql = "DELETE FROM material_chunk_vector WHERE chunk_id IN (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Oracle 向量删除失败：" + e.getMessage(), e);
        }
    }

    /**
     * 相似片段检索：优先走原生向量检索（`VECTOR_DISTANCE(..., COSINE)`，索引存在时即 `HNSW` 近似最近邻）；
     * 失败则 {@code catch} 后**退化为无索引精确检索**。
     */
    @Override
    public List<SearchHit> search(SearchQuery query) {
        float[] vector = query.embedding() != null ? query.embedding() : embeddingClient.embed(query.text());
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        try {
            degraded = false;
            return nativeSearch(query, vector);
        } catch (SQLException | RuntimeException e) {
            // ★ 可退化：Oracle 实例不可用 / HNSW 索引未建成 / VECTOR 绑定失败 ⇒ 退化为精确检索，不判失败
            degraded = true;
            log.warn("Oracle 原生向量检索不可用（{}），退化为无索引精确检索（度量仍是余弦，接口不变）", e.getMessage());
            return scanWithoutIndex(query, vector);
        }
    }

    /** Oracle 原生路径：`VECTOR_DISTANCE(embedding, ?, COSINE)`。 */
    private List<SearchHit> nativeSearch(SearchQuery query, float[] vector) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT chunk_id, course_id, VECTOR_DISTANCE(embedding, ?, COSINE) AS distance "
                        + "FROM material_chunk_vector WHERE 1 = 1");
        List<Long> courses = query.courseIds();
        if (courses != null && !courses.isEmpty()) {
            sql.append(" AND course_id IN (").append(String.join(",", courses.stream().map(x -> "?").toList())).append(")");
        }
        sql.append(" ORDER BY distance FETCH FIRST ").append(query.topK() > 0 ? query.topK() : 8).append(" ROWS ONLY");

        List<SearchHit> hits = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setBytes(1, MysqlVectorStoreImpl.toBytes(vector));
            if (courses != null) {
                for (int i = 0; i < courses.size(); i++) {
                    ps.setLong(i + 2, courses.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double distance = rs.getDouble("distance");
                    // 余弦距离 → 统一换算为"相似度，越大越相关"（与 MySQL 兜底侧口径一致）
                    hits.add(new SearchHit(rs.getLong("chunk_id"), rs.getLong("course_id"), 0L,
                            1.0 - distance, null, null));
                }
            }
        }
        return hits;
    }

    /**
     * 无索引精确检索（退化路径）：全量扫描 + 应用层余弦。
     * ★ 与 `MysqlVectorStoreImpl.search` 的度量**逐字同源**，故两侧 TopK 语义一致。
     */
    private List<SearchHit> scanWithoutIndex(SearchQuery query, float[] vector) {
        StringBuilder sql = new StringBuilder(
                "SELECT chunk_id, course_id, embedding FROM material_chunk_vector WHERE 1 = 1");
        List<Long> courses = query.courseIds();
        if (courses != null && !courses.isEmpty()) {
            sql.append(" AND course_id IN (").append(String.join(",", courses.stream().map(x -> "?").toList())).append(")");
        }
        List<SearchHit> hits = new ArrayList<>();
        int dim = vector.length;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (courses != null) {
                for (int i = 0; i < courses.size(); i++) {
                    ps.setLong(i + 1, courses.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    float[] other = MysqlVectorStoreImpl.toFloats(rs.getBytes("embedding"));
                    if (other.length != dim) {
                        continue;
                    }
                    double dot = 0.0;
                    double normA = 0.0;
                    double normB = 0.0;
                    for (int i = 0; i < dim; i++) {
                        dot += (double) vector[i] * other[i];
                        normA += (double) vector[i] * vector[i];
                        normB += (double) other[i] * other[i];
                    }
                    double denom = Math.sqrt(normA) * Math.sqrt(normB);
                    if (denom == 0.0) {
                        continue;
                    }
                    hits.add(new SearchHit(rs.getLong("chunk_id"), rs.getLong("course_id"), 0L, dot / denom, null, null));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Oracle 退化精确检索也失败：" + e.getMessage(), e);
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        int topK = query.topK() > 0 ? query.topK() : 8;
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    @Override
    public RebuildReport rebuild(Long courseId) {
        long t0 = System.currentTimeMillis();
        List<VectorRecord> records = new ArrayList<>();
        String sql = "SELECT m.id AS chunk_id, m.content AS content, m.chapter AS chapter, m.page_no AS page_no "
                + "FROM material_chunk m JOIN material mat ON mat.id = m.material_id "
                + "WHERE mat.course_id = ? AND m.deleted = 0";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    float[] v = embeddingClient.embed(rs.getString("content"));
                    records.add(new VectorRecord(rs.getLong("chunk_id"), courseId, v.length, v, null));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Oracle 重建失败（读切片）：" + e.getMessage(), e);
        }
        int deleted = delete(records.stream().map(VectorRecord::chunkId).toList());
        int processed = upsert(records);
        return new RebuildReport(courseId, processed, deleted, System.currentTimeMillis() - t0, "oracle");
    }

    @Override
    public String provider() {
        return "oracle";
    }

    /** 上一次检索是否走了退化路径（`HNSW` 未建成时为真）—— 供自检/埋点，**不作任何判据**。 */
    public boolean isDegraded() {
        return degraded;
    }
}
