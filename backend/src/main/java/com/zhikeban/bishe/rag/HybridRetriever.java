package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.domain.entity.MaterialChunk;
import com.zhikeban.bishe.mapper.MaterialChunkMapper;
import com.zhikeban.bishe.mapper.MaterialMapper;
import com.zhikeban.bishe.vector.SearchHit;
import com.zhikeban.bishe.vector.SearchQuery;
import com.zhikeban.bishe.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * **混合检索**（`ENG-012` 任务①；详设 §4.3.2 ②／§4.3.3）。
 *
 * <p>★ 两路**并行且独立降级**：**向量一路**（{@code VectorStore.search}，走 `TB-28`／`TB-27`）
 * ＋ **全文一路**（`material_chunk.content` 的 `FULLTEXT ... ngram`，即 BM25 口径）。
 * 任一路失败 ⇒ 用另一路的结果继续（"检索不中断"）；两路全失败 ⇒ 空结果分支。
 *
 * <p>★ 融合用 **RRF**（Reciprocal Rank Fusion，{@code k = 60}）而**不是**直接加权求和：
 * 余弦相似度与 BM25 分数**不同量纲**，直接加权会引入不可解释的权重；RRF 只依赖排名，跨度量稳健。
 *
 * <p>★ 去重键 = {@code chunkId}（同一片段被两路同时召回是常态，提示词里只应出现一次），
 * 融合后按分降序截断 TopK（详设默认 Top5）。
 */
@Service
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    /** RRF 的平滑常数（详设 §4.3.3 明写 `k = 60`）。 */
    private static final int RRF_K = 60;

    /** 单路召回条数（详设 §4.3.3：向量一路 TopK=8、全文一路 TopK=8）。 */
    private static final int PER_PATH_TOPK = 8;

    private final VectorStore vectorStore;
    private final MaterialChunkMapper materialChunkMapper;
    private final MaterialMapper materialMapper;

    public HybridRetriever(VectorStore vectorStore, MaterialChunkMapper materialChunkMapper,
                           MaterialMapper materialMapper) {
        this.vectorStore = vectorStore;
        this.materialChunkMapper = materialChunkMapper;
        this.materialMapper = materialMapper;
    }

    /**
     * 混合召回：两路并行 → RRF 融合 → 去重 → 截断。
     *
     * @param stmt     检索语句（已做指代消解）
     * @param courseId 课程级预过滤（成员级过滤是 {@link PermissionFilter} 的职责）
     * @param topK     融合后保留条数（{@code <=0} 时用详设默认 5）
     * @return 融合后的片段（按融合分降序）
     */
    public List<RetrievedChunk> retrieve(String stmt, Long courseId, int topK) {
        int want = topK > 0 ? topK : 5;
        CompletableFuture<List<RetrievedChunk>> vectorFuture =
                CompletableFuture.supplyAsync(() -> vectorPath(stmt, courseId));
        CompletableFuture<List<RetrievedChunk>> textFuture =
                CompletableFuture.supplyAsync(() -> fullTextPath(stmt, courseId));
        List<RetrievedChunk> vectorHits = await(vectorFuture, "向量一路");
        List<RetrievedChunk> textHits = await(textFuture, "全文一路");
        return fuse(vectorHits, textHits, want);
    }

    /** 向量一路：`VectorStore.search`（分数是**原始余弦相似度**，只在内部用于排名）。 */
    private List<RetrievedChunk> vectorPath(String stmt, Long courseId) {
        try {
            List<SearchHit> hits = vectorStore.search(SearchQuery.of(stmt, courseId, PER_PATH_TOPK));
            List<RetrievedChunk> out = new ArrayList<>();
            for (SearchHit h : hits) {
                out.add(new RetrievedChunk(h.chunkId(), h.materialId(), null,
                        chapterOf(h.metadata()), pageOf(h.metadata()), h.score(), h.content()));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("向量一路失败，降级为仅全文一路：{}", e.getMessage());
            return List.of();
        }
    }

    /** 全文一路：`material_chunk.content` 的 BM25 / ngram 命中。 */
    private List<RetrievedChunk> fullTextPath(String stmt, Long courseId) {
        try {
            List<MaterialChunk> chunks = materialChunkMapper.searchFullText(courseId, stmt, PER_PATH_TOPK);
            List<RetrievedChunk> out = new ArrayList<>();
            for (MaterialChunk c : chunks) {
                out.add(new RetrievedChunk(c.getId(), c.getMaterialId(), null,
                        c.getChapter(), c.getPageNo(), 0.0d, c.getContent()));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("全文一路失败，降级为仅向量一路：{}", e.getMessage());
            return List.of();
        }
    }

    /** RRF 融合：{@code score = Σ 1 / (k + rank_i)}，按 chunkId 去重后截断。 */
    private List<RetrievedChunk> fuse(List<RetrievedChunk> a, List<RetrievedChunk> b, int want) {
        Map<Long, Double> fused = new HashMap<>();
        Map<Long, RetrievedChunk> byId = new LinkedHashMap<>();
        accumulate(a, fused, byId);
        accumulate(b, fused, byId);
        List<RetrievedChunk> merged = new ArrayList<>();
        for (Map.Entry<Long, RetrievedChunk> e : byId.entrySet()) {
            RetrievedChunk c = e.getValue();
            merged.add(new RetrievedChunk(c.chunkId(), c.materialId(), c.materialName(),
                    c.chapter(), c.pageNo(), fused.getOrDefault(e.getKey(), 0.0d), c.content()));
        }
        merged.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        List<RetrievedChunk> top = merged.size() > want ? new ArrayList<>(merged.subList(0, want)) : merged;
        return withMaterialNames(top);
    }

    private void accumulate(List<RetrievedChunk> hits, Map<Long, Double> fused, Map<Long, RetrievedChunk> byId) {
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk c = hits.get(i);
            if (c.chunkId() == null) {
                continue;
            }
            double rrf = 1.0d / (RRF_K + i + 1);
            fused.merge(c.chunkId(), rrf, Double::sum);
            byId.putIfAbsent(c.chunkId(), c);
        }
    }

    /** 补齐资料名（一次批量查 `material`，避免 N+1）。 */
    private List<RetrievedChunk> withMaterialNames(List<RetrievedChunk> hits) {
        Set<Long> ids = new LinkedHashSet<>();
        for (RetrievedChunk c : hits) {
            if (c.materialId() != null && c.materialId() > 0) {
                ids.add(c.materialId());
            }
        }
        if (ids.isEmpty()) {
            return hits;
        }
        Map<Long, String> names = new HashMap<>();
        try {
            for (Material m : materialMapper.selectBatchIds(ids)) {
                names.put(m.getId(), m.getName());
            }
        } catch (RuntimeException e) {
            log.warn("回填资料名失败（不影响检索结果）：{}", e.getMessage());
        }
        List<RetrievedChunk> out = new ArrayList<>(hits.size());
        for (RetrievedChunk c : hits) {
            out.add(new RetrievedChunk(c.chunkId(), c.materialId(), names.get(c.materialId()),
                    c.chapter(), c.pageNo(), c.score(), c.content()));
        }
        return out;
    }

    private List<RetrievedChunk> await(CompletableFuture<List<RetrievedChunk>> future, String which) {
        try {
            List<RetrievedChunk> r = future.join();
            return r == null ? List.of() : r;
        } catch (RuntimeException e) {
            log.warn("{} 抛出异常，按空结果继续：{}", which, e.getMessage());
            return List.of();
        }
    }

    /** 从 `SearchHit.metadata` 的 JSON 文本里取章节（取不到返回 {@code null}）。 */
    static String chapterOf(String metadata) {
        return jsonField(metadata, "chapter");
    }

    /** 从 `SearchHit.metadata` 的 JSON 文本里取页码（取不到返回 {@code null}）。 */
    static Integer pageOf(String metadata) {
        String v = jsonField(metadata, "pageNo");
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String jsonField(String metadata, String key) {
        if (metadata == null) {
            return null;
        }
        int at = metadata.indexOf("\"" + key + "\"");
        if (at < 0) {
            return null;
        }
        int colon = metadata.indexOf(':', at);
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < metadata.length() && Character.isWhitespace(metadata.charAt(i))) {
            i++;
        }
        if (i >= metadata.length()) {
            return null;
        }
        if (metadata.charAt(i) == '"') {
            int end = metadata.indexOf('"', i + 1);
            return end < 0 ? null : metadata.substring(i + 1, end);
        }
        int end = i;
        while (end < metadata.length() && ",}".indexOf(metadata.charAt(end)) < 0) {
            end++;
        }
        return metadata.substring(i, end).trim();
    }
}
