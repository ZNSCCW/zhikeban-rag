package com.zhikeban.bishe.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 向量存储 · **MySQL 兜底实现**（`ENG-012` 任务②；默认路径，走 `TB-28` `chunk_embedding`）。
 *
 * <p>★ 口径（详设 §4.3.9 的两侧一致性表）：**不建向量索引**，全量扫描 + **应用层余弦相似度**
 * {@code cos(a,b) = a·b / (‖a‖·‖b‖)}；检索前**校验维度**，不一致即**拒绝**（不"算出错误结果"）。
 * 与 Oracle 侧的 `COSINE` **度量一致** ⇒ 切换实现不改变 TopK 的相对次序。
 *
 * <p>★ 序列化：`float32` 小端序写入 `TB-28.embedding`（`BLOB(4096)` = 1024 × 4）。
 *
 * <p>★ 本类**只做课程级预过滤**；成员级权限过滤是 `PermissionFilter` 的职责（详设 §3.4 边界）。
 */
@Service("mysqlVectorStore")
@Profile("vector-mysql")
public class MysqlVectorStoreImpl implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(MysqlVectorStoreImpl.class);

    private final DataSource dataSource;
    private final EmbeddingClient embeddingClient;

    public MysqlVectorStoreImpl(DataSource dataSource, EmbeddingClient embeddingClient) {
        this.dataSource = dataSource;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public int upsert(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO chunk_embedding (chunk_id, course_id, embedding, dim, metadata) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE course_id = VALUES(course_id), embedding = VALUES(embedding), "
                + "dim = VALUES(dim), metadata = VALUES(metadata)";
        int affected = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (VectorRecord r : records) {
                if (r == null || r.chunkId() == null) {
                    continue;
                }
                ps.setLong(1, r.chunkId());
                ps.setLong(2, r.courseId() == null ? 0L : r.courseId());
                ps.setBytes(3, toBytes(r.embedding()));
                ps.setInt(4, r.dim());
                ps.setString(5, r.metadata());
                ps.addBatch();
            }
            for (int n : ps.executeBatch()) {
                affected += Math.max(n, 0);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("MySQL 向量写入失败：" + e.getMessage(), e);
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
        String sql = "DELETE FROM chunk_embedding WHERE chunk_id IN (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("MySQL 向量删除失败：" + e.getMessage(), e);
        }
    }

    /**
     * 相似片段检索：全量扫描该课程范围的行，**应用层算余弦相似度**，再取 TopK。
     *
     * <p>★ 检索前校验 {@code dim}：库内维度和查询向量维度**不一致即拒绝**
     * （宁拒绝，也不拿错误维度的向量去算余弦 —— 那会静默产出无意义的分数）。
     */
    @Override
    public List<SearchHit> search(SearchQuery query) {
        Objects.requireNonNull(query, "query 不能为空");
        float[] vector = query.embedding() != null ? query.embedding() : embeddingClient.embed(query.text());
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        int dim = vector.length;

        StringBuilder sql = new StringBuilder(
                "SELECT c.chunk_id, c.course_id, c.embedding, c.dim, c.metadata, "
                        + "m.content AS content, m.material_id AS material_id "
                        + "FROM chunk_embedding c LEFT JOIN material_chunk m ON m.id = c.chunk_id WHERE 1 = 1");
        List<Long> courses = query.courseIds();
        if (courses != null && !courses.isEmpty()) {
            sql.append(" AND c.course_id IN (").append(String.join(",", courses.stream().map(x -> "?").toList())).append(")");
        }

        List<SearchHit> hits = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (courses != null) {
                for (int i = 0; i < courses.size(); i++) {
                    ps.setLong(i + 1, courses.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int rowDim = rs.getInt("dim");
                    if (rowDim != dim) {
                        throw new IllegalStateException("向量维度不一致：TB-28.dim=" + rowDim
                                + "，查询向量维度=" + dim + " ⇒ 拒绝检索（换嵌入模型时必须三格同改，详设 §4.3.8）");
                    }
                    float[] other = toFloats(rs.getBytes("embedding"));
                    double dot = 0.0;
                    double normA = 0.0;
                    double normB = 0.0;
                    for (int i = 0; i < dim && i < other.length; i++) {
                        dot += (double) vector[i] * other[i];
                        normA += (double) vector[i] * vector[i];
                        normB += (double) other[i] * other[i];
                    }
                    double denom = Math.sqrt(normA) * Math.sqrt(normB);
                    if (denom == 0.0) {
                        continue;
                    }
                    double score = dot / denom;
                    long materialId = rs.getLong("material_id");
                    if (rs.wasNull()) {
                        materialId = 0L;
                    }
                    hits.add(new SearchHit(rs.getLong("chunk_id"), rs.getLong("course_id"), materialId,
                            score, rs.getString("content"), rs.getString("metadata")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("MySQL 向量检索失败：" + e.getMessage(), e);
        }

        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        int topK = query.topK() > 0 ? query.topK() : 8;
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    @Override
    public RebuildReport rebuild(Long courseId) {
        long t0 = System.currentTimeMillis();
        String sql = "SELECT m.id AS chunk_id, m.content AS content, m.chapter AS chapter, m.page_no AS page_no, "
                + "mat.name AS material_name, mat.course_id AS course_id "
                + "FROM material_chunk m JOIN material mat ON mat.id = m.material_id "
                + "WHERE mat.course_id = ? AND m.deleted = 0 AND mat.deleted = 0";
        List<VectorRecord> records = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content");
                    float[] v = embeddingClient.embed(content);
                    String metadata = "{\"materialName\":\"" + safe(rs.getString("material_name"))
                            + "\",\"chapter\":\"" + safe(rs.getString("chapter"))
                            + "\",\"pageNo\":" + rs.getInt("page_no") + "}";
                    records.add(new VectorRecord(rs.getLong("chunk_id"), rs.getLong("course_id"),
                            v.length, v, metadata));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("重建向量失败（读切片）：" + e.getMessage(), e);
        }
        int deleted = delete(records.stream().map(VectorRecord::chunkId).toList());
        int processed = upsert(records);
        return new RebuildReport(courseId, processed, deleted, System.currentTimeMillis() - t0, provider());
    }

    @Override
    public String provider() {
        return "mysql";
    }

    static byte[] toBytes(float[] v) {
        if (v == null) {
            return new byte[0];
        }
        ByteBuffer buf = ByteBuffer.allocate(v.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) {
            buf.putFloat(x);
        }
        return buf.array();
    }

    static float[] toFloats(byte[] bytes) {
        if (bytes == null || bytes.length < Float.BYTES) {
            return new float[0];
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < v.length; i++) {
            v[i] = buf.getFloat();
        }
        return v;
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 供自检：当前库内某课程的向量条数（只读）。 */
    public int countByCourse(Long courseId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM chunk_embedding WHERE course_id = ?")) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            log.warn("统计向量条数失败：{}", e.getMessage());
            return -1;
        }
    }
}
