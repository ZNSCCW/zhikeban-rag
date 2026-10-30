package com.zhikeban.bishe.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikeban.bishe.vector.EmbeddingClient;
import com.zhikeban.bishe.vector.OllamaEmbeddingClient;
import com.zhikeban.bishe.vector.VectorRecord;
import com.zhikeban.bishe.vector.VectorStore;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 端到端冒烟**执行体**（`ENG-012` 任务⑤；由 `.tools/eng012-smoke.mjs` 用
 * {@code java -jar … --zhikeban.smoke.enabled=true} 拉起）。
 *
 * <p>★ 它做的是**真事**，不是"打印一段自检文本"：
 * <ol>
 *   <li>**真 HTTP**：向本进程刚起的 Tomcat 发 {@code GET /api/health}，要求 {@code status=UP}；</li>
 *   <li>**真写库**：往 compose 的 MySQL 写冒烟用的 user / course / material / material_chunk；</li>
 *   <li>**真嵌入 + 真写向量**：调 Ollama 的 {@code bge-m3} 取 **1024 维**向量，经
 *       `VectorStoreRouter` → `MysqlVectorStoreImpl` 写进 `TB-28`；</li>
 *   <li>**真算余弦**：`search` 全量扫描 + 应用层余弦，取 TopK；</li>
 *   <li>**真走六阶段**：{@code RagOrchestrator.run} 串起检索 / 过滤 / 提示词 / 生成 / 引用；</li>
 *   <li>**真跑降级链**：本地模型（Ollama `qwen2.5:7b`）→ API 备选 → 全文检索兜底；</li>
 *   <li>**真判越权负例**：非成员调 `PermissionFilter` 必须被拒（403）。</li>
 * </ol>
 *
 * <p>★ 输出协议：把结果以**一行** JSON 打印在标记 {@value #MARKER} 之后；
 * 冒烟脚本只读这一行，其余 stdout 仅供人读排错。
 */
@Component
@ConditionalOnProperty(name = "zhikeban.smoke.enabled", havingValue = "true")
public class RagSmokeRunner implements ApplicationRunner {

    /** 机器可读结果的起始标记（冒烟脚本按它截取）。 */
    public static final String MARKER = "@@ENG012_STAGES@@";

    private static final String SEED_USER = "eng012-smoke";
    private static final String SEED_INVITE = "ENG012SM";
    private static final String SEED_MATERIAL = "ENG-012 冒烟资料.pdf";

    private static final List<String> SEED_CHUNKS = List.of(
            "余弦相似度是两个向量夹角的余弦值，等于点积除以两个向量模长的乘积，取值范围是负一到一。",
            "混合检索把向量一路与全文一路的召回结果用 RRF 融合，再按 chunkId 去重后截断 TopK。",
            "引用回填只保留落在本次检索编号范围内的引用，越界编号视为模型编造并剔除。");

    private final DataSource dataSource;
    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    private final RagOrchestrator orchestrator;
    private final PermissionFilter permissionFilter;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final int port;

    private final List<Map<String, Object>> stages = new ArrayList<>();

    public RagSmokeRunner(DataSource dataSource, VectorStore vectorStore, EmbeddingClient embeddingClient,
                          RagOrchestrator orchestrator, PermissionFilter permissionFilter,
                          @Value("${server.port:8080}") int port) {
        this.dataSource = dataSource;
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.orchestrator = orchestrator;
        this.permissionFilter = permissionFilter;
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean ok = false;
        try {
            ok = smoke(out);
        } catch (Exception e) {
            add("冒烟执行", "RUNNER", false, e.getClass().getSimpleName() + "：" + e.getMessage());
            out.put("fatal", e.getClass().getName() + "：" + e.getMessage());
        }
        out.put("ok", ok);
        out.put("vectorProvider", vectorStore.provider());
        out.put("embeddingProvider", embeddingClient.provider());
        out.put("embeddingDim", embeddingClient.dim());
        out.put("stages", stages);
        try {
            System.out.println(MARKER + mapper.writeValueAsString(out));
        } catch (Exception e) {
            System.out.println(MARKER + "{\"ok\":false,\"fatal\":\"结果序列化失败\"}");
        }
        System.out.flush();
        System.exit(ok ? 0 : 1);
    }

    private boolean smoke(Map<String, Object> out) throws Exception {
        /* ── 0. 真 HTTP：本进程刚起的 Tomcat 必须能答 /api/health ───────────── */
        long t0 = System.currentTimeMillis();
        String healthUrl = "http://127.0.0.1:" + port + "/api/health";
        boolean healthOk = false;
        String healthDetail;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body() == null ? "" : resp.body();
            healthOk = resp.statusCode() == 200 && body.contains("\"status\":\"UP\"");
            healthDetail = "GET " + healthUrl + " → " + resp.statusCode() + " " + body;
        } catch (Exception e) {
            healthDetail = "GET " + healthUrl + " 失败：" + e.getMessage();
        }
        add("环境 · HTTP 健康探活", "HTTP", healthOk, healthDetail, System.currentTimeMillis() - t0);

        /* ── 1. 真写库：冒烟数据（user / course / material / material_chunk）── */
        t0 = System.currentTimeMillis();
        long userId;
        long courseId;
        long materialId;
        List<Long> chunkIds = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            userId = ensureUser(conn);
            courseId = ensureCourse(conn, userId);
            materialId = ensureMaterial(conn, courseId, userId);
            ensureChunks(conn, materialId, chunkIds, contents);
        }
        add("数据准备 · 冒烟数据落库", "SEED", userId > 0 && courseId > 0 && materialId > 0 && chunkIds.size() == SEED_CHUNKS.size(),
                "userId=" + userId + " courseId=" + courseId + " materialId=" + materialId + " chunks=" + chunkIds.size(),
                System.currentTimeMillis() - t0);
        out.put("seed", Map.of("userId", userId, "courseId", courseId, "materialId", materialId, "chunkIds", chunkIds));

        /* ── 2. 真嵌入 + 真写向量（TB-28，默认路径）────────────────────────── */
        t0 = System.currentTimeMillis();
        List<VectorRecord> records = new ArrayList<>();
        int dim = 0;
        for (int i = 0; i < chunkIds.size(); i++) {
            float[] v = embeddingClient.embed(contents.get(i));
            dim = v.length;
            String metadata = "{\"materialName\":\"" + SEED_MATERIAL + "\",\"chapter\":\"第1章\",\"pageNo\":" + (i + 1) + "}";
            records.add(new VectorRecord(chunkIds.get(i), courseId, v.length, v, metadata));
        }
        int written = vectorStore.upsert(records);
        add("数据准备 · 向量写入 " + vectorStore.provider(), "VECTOR_UPSERT",
                written >= 1 && dim == OllamaEmbeddingClient.EMBED_DIM,
                "provider=" + vectorStore.provider() + " 写入 " + written + " 条 · 嵌入维度=" + dim
                        + "（bge-m3 契约 1024）· 嵌入提供方=" + embeddingClient.provider(),
                System.currentTimeMillis() - t0);

        /* ── 3. 真跑六阶段（编排层自己留 trace）────────────────────────────── */
        String question = "余弦相似度是怎么定义的？";
        RagAnswer answer = orchestrator.run(new RagRequest(courseId, null, userId, question, true));
        for (RagTrace trace : orchestrator.traces()) {
            add(trace.label(), trace.stage(), trace.ok(), trace.detail(), trace.ms());
        }
        out.put("answer", Map.of(
                "question", question,
                "contentLength", answer.content() == null ? 0 : answer.content().length(),
                "contentHead", head(answer.content()),
                "citations", answer.citations().size(),
                "degraded", answer.degraded(),
                "degradeReason", answer.degradeReason() == null ? "" : answer.degradeReason()));

        /* ── 4. 真判越权负例：非成员必须被拒（403）────────────────────────── */
        t0 = System.currentTimeMillis();
        boolean denied = false;
        String denyDetail;
        try {
            List<RetrievedChunk> hits = new ArrayList<>();
            hits.add(new RetrievedChunk(chunkIds.get(0), materialId, SEED_MATERIAL, "第1章", 1, 1.0d, contents.get(0)));
            permissionFilter.apply(hits, 999_999_999L, courseId);
            denyDetail = "非成员未被拒（越权防线失效）";
        } catch (ApiException e) {
            denied = e.getStatus() == 403;
            denyDetail = "非成员被拒：status=" + e.getStatus() + " msg=" + e.getMessage();
        } catch (RuntimeException e) {
            denyDetail = "非成员请求抛出非 ApiException：" + e;
        }
        add("权限负例 · 非成员被拒", "PERMISSION_NEGATIVE", denied, denyDetail, System.currentTimeMillis() - t0);

        boolean allOk = stages.stream().allMatch(s -> Boolean.TRUE.equals(s.get("ok")));
        out.put("stageOk", allOk);
        return allOk;
    }

    private static String head(String content) {
        if (content == null) {
            return "";
        }
        String s = content.replaceAll("\\s+", " ").strip();
        return s.length() <= 160 ? s : s.substring(0, 160) + "…";
    }

    private void add(String label, String stage, boolean ok, String detail) {
        add(label, stage, ok, detail, 0L);
    }

    private void add(String label, String stage, boolean ok, String detail, long ms) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("label", label);
        s.put("stage", stage);
        s.put("ok", ok);
        s.put("detail", detail == null ? "" : detail);
        s.put("ms", ms);
        stages.add(s);
    }

    /* ────────────────────────── 冒烟数据（幂等）────────────────────────── */

    private long ensureUser(Connection conn) throws SQLException {
        Long id = queryLong(conn, "SELECT id FROM `user` WHERE username = ?", SEED_USER);
        if (id != null) {
            return id;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO `user` (username, password, nickname, status, deleted) VALUES (?, ?, ?, 1, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, SEED_USER);
            ps.setString(2, "$2a$10$eng012smokeplaceholderplaceholderplaceholderplaceholder");
            ps.setString(3, "ENG-012 冒烟用户");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    private long ensureCourse(Connection conn, long teacherId) throws SQLException {
        Long id = queryLong(conn, "SELECT id FROM course WHERE invite_code = ?", SEED_INVITE);
        if (id != null) {
            return id;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO course (name, teacher_id, semester, invite_code, status, deleted) VALUES (?, ?, ?, ?, 1, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "ENG-012 冒烟课程");
            ps.setLong(2, teacherId);
            ps.setString(3, "2026-2027-1");
            ps.setString(4, SEED_INVITE);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    private long ensureMaterial(Connection conn, long courseId, long uploaderId) throws SQLException {
        Long id = queryLong(conn, "SELECT id FROM material WHERE course_id = ? AND name = ?", courseId, SEED_MATERIAL);
        if (id != null) {
            return id;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO material (course_id, name, type, size, storage_path, chapter, uploader_id, parse_status, download_count, deleted) "
                        + "VALUES (?, ?, 'PDF', 1024, 'smoke/eng012.pdf', '第1章', ?, 1, 0, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, courseId);
            ps.setString(2, SEED_MATERIAL);
            ps.setLong(3, uploaderId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    private void ensureChunks(Connection conn, long materialId, List<Long> ids, List<String> contents) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, content FROM material_chunk WHERE material_id = ? AND deleted = 0 ORDER BY chunk_index")) {
            ps.setLong(1, materialId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                    contents.add(rs.getString("content"));
                }
            }
        }
        if (ids.size() == SEED_CHUNKS.size()) {
            return;
        }
        ids.clear();
        contents.clear();
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM material_chunk WHERE material_id = ?")) {
            del.setLong(1, materialId);
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO material_chunk (material_id, chunk_index, content, page_no, chapter, deleted) VALUES (?, ?, ?, ?, '第1章', 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < SEED_CHUNKS.size(); i++) {
                ins.setLong(1, materialId);
                ins.setInt(2, i);
                ins.setString(3, SEED_CHUNKS.get(i));
                ins.setInt(4, i + 1);
                ins.executeUpdate();
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    ids.add(keys.next() ? keys.getLong(1) : -1L);
                }
                contents.add(SEED_CHUNKS.get(i));
            }
        }
    }

    private Long queryLong(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }
}
