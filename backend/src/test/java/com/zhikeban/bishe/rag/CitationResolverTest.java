package com.zhikeban.bishe.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `CitationResolver` 单测（`ENG-013`）—— "引用溯源"创新点的**机器化**：
 * 只保留落在本次检索编号范围内的引用，**越界编号视为编造并剔除**。
 */
class CitationResolverTest {

    private final CitationResolver resolver = new CitationResolver();

    private static RetrievedChunk chunk(long id, Long materialId, String name, String chapter, Integer pageNo, String content) {
        return new RetrievedChunk(id, materialId, name, chapter, pageNo, 1.0d, content);
    }

    private static List<RetrievedChunk> twoHits() {
        return List.of(
                chunk(1L, 10L, "资料A", "第1章", 1, "甲片段"),
                chunk(2L, 11L, "资料B", "第2章", 2, "乙片段"));
    }

    @Test
    void keepsOnlyInRangeNumbersAndBackfillsFieldsFromRetrievalLayer() {
        RagAnswer answer = resolver.resolve(List.of("答案 ", "[2]", " 与 ", "[1]"), twoHits(), 42L);

        assertThat(answer.messageId()).isEqualTo(42L);
        assertThat(answer.content()).isEqualTo("答案 [2] 与 [1]");
        assertThat(answer.citations()).hasSize(2);
        // ★ 引用次序按正文出现次序（[2] 在前）
        assertThat(answer.citations().get(0).materialId()).isEqualTo(11L);
        assertThat(answer.citations().get(0).materialName()).isEqualTo("资料B");
        assertThat(answer.citations().get(0).chapter()).isEqualTo("第2章");
        assertThat(answer.citations().get(0).pageNo()).isEqualTo(2);
        assertThat(answer.citations().get(1).materialId()).isEqualTo(10L);
        assertThat(answer.degraded()).isFalse();
        assertThat(answer.cacheHit()).isFalse();
    }

    @Test
    void outOfRangeNumbersAreDroppedAsFabricated() {
        RagAnswer answer = resolver.resolve(List.of("答案 [7] 与 [0] 与 [99]"), twoHits(), null);

        // 全部越界 ⇒ 退化为"服务端回填前 3 片"，绝不产出编造引用
        assertThat(answer.citations()).hasSize(2);
        assertThat(answer.citations()).extracting(Citation::materialId).containsExactly(10L, 11L);
    }

    @Test
    void mixedInRangeAndOutOfRangeKeepsOnlyInRange() {
        RagAnswer answer = resolver.resolve(List.of("答案 [1] 与 [7]"), twoHits(), null);

        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.citations().get(0).materialId()).isEqualTo(10L);
        assertThat(answer.content()).isEqualTo("答案 [1] 与 [7]");
    }

    @Test
    void whenModelGivesNoNumberServerBackfillsAtMostThreeHits() {
        List<RetrievedChunk> hits = List.of(
                chunk(1L, 10L, "A", null, null, "a"),
                chunk(2L, 11L, "B", null, null, "b"),
                chunk(3L, 12L, "C", null, null, "c"),
                chunk(4L, 13L, "D", null, null, "d"));

        RagAnswer answer = resolver.resolve(List.of("模型没写编号"), hits, null);

        assertThat(answer.citations()).hasSize(3);
        assertThat(answer.citations()).extracting(Citation::materialId).containsExactly(10L, 11L, 12L);
    }

    @Test
    void nullDeltasAndNullHitsAreHandled() {
        RagAnswer answer = resolver.resolve(null, null, null);

        assertThat(answer.content()).isEmpty();
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void degradeInformationIsCarriedThrough() {
        RagAnswer answer = resolver.resolve(List.of("兜底正文 [1]"), twoHits(), 7L, true, "本地模型不可用");

        assertThat(answer.degraded()).isTrue();
        assertThat(answer.degradeReason()).isEqualTo("本地模型不可用");
        assertThat(answer.citations()).hasSize(1);
    }

    @Test
    void snippetIsTrimmedAndTruncatedTo120Chars() {
        assertThat(CitationResolver.snippet(null)).isEmpty();
        assertThat(CitationResolver.snippet("  短  ")).isEqualTo("短");

        String longText = "字".repeat(200);
        String snippet = CitationResolver.snippet(longText);
        assertThat(snippet).hasSize(121);
        assertThat(snippet).endsWith("…");
    }
}
