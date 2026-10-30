package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.domain.entity.MaterialChunk;
import com.zhikeban.bishe.mapper.MaterialChunkMapper;
import com.zhikeban.bishe.mapper.MaterialMapper;
import com.zhikeban.bishe.vector.SearchHit;
import com.zhikeban.bishe.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `HybridRetriever` 单测（`ENG-013`）：两路召回 ＋ **RRF 融合** ＋ 去重 ＋ TopK ＋ 两路**独立降级**。
 *
 * <p>★ 断言的都是**可观测行为**（融合后的次序与分数、降级后的结果集），不是"方法被调用过"。
 */
class HybridRetrieverTest {

    private VectorStore vectorStore;
    private MaterialChunkMapper chunkMapper;
    private MaterialMapper materialMapper;
    private HybridRetriever retriever;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        chunkMapper = mock(MaterialChunkMapper.class);
        materialMapper = mock(MaterialMapper.class);
        retriever = new HybridRetriever(vectorStore, chunkMapper, materialMapper);
        when(vectorStore.search(any())).thenReturn(List.of());
        when(chunkMapper.searchFullText(any(), anyString(), anyInt())).thenReturn(List.of());
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
    }

    private static SearchHit hit(long chunkId, long materialId, double score, String content, String metadata) {
        return new SearchHit(chunkId, 10L, materialId, score, content, metadata);
    }

    private static MaterialChunk chunk(long id, long materialId, String content, String chapter, Integer pageNo) {
        MaterialChunk c = new MaterialChunk();
        c.setId(id);
        c.setMaterialId(materialId);
        c.setContent(content);
        c.setChapter(chapter);
        c.setPageNo(pageNo);
        return c;
    }

    private static Material material(long id, String name) {
        Material m = new Material();
        m.setId(id);
        m.setName(name);
        return m;
    }

    @Test
    void retrieve_fusesTwoPathsByRrfAndDedupesByChunkId() {
        when(vectorStore.search(any())).thenReturn(List.of(
                hit(1L, 100L, 0.9d, "向量命中一", "{\"chapter\":\"第1章\",\"pageNo\":3}"),
                hit(2L, 101L, 0.8d, "向量命中二", null)));
        when(chunkMapper.searchFullText(any(), anyString(), anyInt())).thenReturn(List.of(
                chunk(2L, 101L, "全文命中二", "第2章", 7),
                chunk(3L, 102L, "全文命中三", null, null)));
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                material(100L, "资料A"), material(101L, "资料B"), material(102L, "资料C")));

        List<RetrievedChunk> out = retriever.retrieve("余弦相似度", 10L, 5);

        // 同一 chunkId 被两路同时召回 ⇒ 只出现一次；次序按 RRF 融合分降序（2 出现在两路 ⇒ 最高）
        assertThat(out).extracting(RetrievedChunk::chunkId).containsExactly(2L, 1L, 3L);
        assertThat(out).extracting(RetrievedChunk::materialName).containsExactly("资料B", "资料A", "资料C");
        // RRF：chunk2 = 1/(60+2) + 1/(60+1)；chunk1 = 1/(60+1)
        assertThat(out.get(0).score()).isCloseTo(1.0d / 62 + 1.0d / 61, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(out.get(1).score()).isCloseTo(1.0d / 61, org.assertj.core.data.Offset.offset(1e-9));
        // 向量一路的章节/页码来自 metadata；全文一路的来自实体列
        assertThat(out.get(1).chapter()).isEqualTo("第1章");
        assertThat(out.get(1).pageNo()).isEqualTo(3);
        assertThat(out.get(2).chapter()).isNull();
    }

    @Test
    void retrieve_truncatesToTopKAndFallsBackToDefaultWhenNonPositive() {
        List<SearchHit> hits = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            hits.add(hit(i, 100L + i, 1.0d - i * 0.01d, "c" + i, null));
        }
        when(vectorStore.search(any())).thenReturn(hits);

        assertThat(retriever.retrieve("q", 10L, 2)).hasSize(2);
        // topK <= 0 ⇒ 详设默认 5
        assertThat(retriever.retrieve("q", 10L, 0)).hasSize(5);
        assertThat(retriever.retrieve("q", 10L, -3)).hasSize(5);
    }

    @Test
    void vectorPath_degradesToFullTextOnlyWhenStoreThrows() {
        when(vectorStore.search(any())).thenThrow(new IllegalStateException("向量库不可用"));
        when(chunkMapper.searchFullText(any(), anyString(), anyInt())).thenReturn(List.of(
                chunk(9L, 900L, "仅全文一路", "第3章", 11)));

        List<RetrievedChunk> out = retriever.retrieve("q", 10L, 5);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).chunkId()).isEqualTo(9L);
        assertThat(out.get(0).content()).isEqualTo("仅全文一路");
    }

    @Test
    void fullTextPath_degradesToVectorOnlyWhenMapperThrows() {
        when(vectorStore.search(any())).thenReturn(List.of(hit(5L, 500L, 0.7d, "仅向量一路", null)));
        when(chunkMapper.searchFullText(any(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("全文索引不可用"));

        List<RetrievedChunk> out = retriever.retrieve("q", 10L, 5);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).chunkId()).isEqualTo(5L);
    }

    @Test
    void bothPathsFail_returnsEmptyResultInsteadOfThrowing() {
        when(vectorStore.search(any())).thenThrow(new IllegalStateException("向量库不可用"));
        when(chunkMapper.searchFullText(any(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("全文索引不可用"));

        assertThat(retriever.retrieve("q", 10L, 5)).isEmpty();
    }

    @Test
    void nullFromEitherPathIsTreatedAsNoHits() {
        when(vectorStore.search(any())).thenReturn(null);
        when(chunkMapper.searchFullText(any(), anyString(), anyInt())).thenReturn(null);

        assertThat(retriever.retrieve("q", 10L, 5)).isEmpty();
    }

    @Test
    void pathThrowingAnErrorIsContainedByAwait() {
        // 任一路抛 Error（非 RuntimeException）时，await 也不能把整条链路带崩
        when(vectorStore.search(any())).thenThrow(new AssertionError("boom"));
        when(chunkMapper.searchFullText(any(), anyString(), anyInt())).thenReturn(List.of(
                chunk(4L, 400L, "全文仍在", null, null)));

        List<RetrievedChunk> out = retriever.retrieve("q", 10L, 5);

        assertThat(out).extracting(RetrievedChunk::chunkId).containsExactly(4L);
    }

    @Test
    void materialNameBackfillFailureDoesNotLoseHits() {
        when(vectorStore.search(any())).thenReturn(List.of(hit(6L, 600L, 0.5d, "内容", null)));
        when(materialMapper.selectBatchIds(anyCollection())).thenThrow(new IllegalStateException("库挂了"));

        List<RetrievedChunk> out = retriever.retrieve("q", 10L, 5);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).materialName()).isNull();
        assertThat(out.get(0).content()).isEqualTo("内容");
    }

    @Test
    void namesAreNotQueriedWhenNoUsableMaterialId() {
        // materialId 为 null / <=0 ⇒ 不发批量查询（避免无意义 SQL），片段照样返回
        when(vectorStore.search(any())).thenReturn(List.of(
                hit(7L, 0L, 0.4d, "无资料归属", null)));

        List<RetrievedChunk> out = retriever.retrieve("q", 10L, 5);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).materialName()).isNull();
    }

    @Test
    void metadataJsonIsParsedTolerantly() {
        assertThat(HybridRetriever.chapterOf("{\"chapter\":\"第4章\",\"pageNo\":9}")).isEqualTo("第4章");
        assertThat(HybridRetriever.pageOf("{\"chapter\":\"第4章\",\"pageNo\":9}")).isEqualTo(9);
        assertThat(HybridRetriever.chapterOf(null)).isNull();
        assertThat(HybridRetriever.pageOf(null)).isNull();
        assertThat(HybridRetriever.jsonField("{\"other\":1}", "chapter")).isNull();
        assertThat(HybridRetriever.jsonField("{\"chapter\" 1}", "chapter")).isNull();
        assertThat(HybridRetriever.jsonField("{\"chapter\":", "chapter")).isNull();
        // 值不是数字 ⇒ 页码返回 null（不抛异常）
        assertThat(HybridRetriever.pageOf("{\"pageNo\":\"第三页\"}")).isNull();
        assertThat(HybridRetriever.pageOf("{\"pageNo\":\"\"}")).isNull();
        // 数字型未加引号也能取
        assertThat(HybridRetriever.jsonField("{\"pageNo\":12}", "pageNo")).isEqualTo("12");
    }
}
