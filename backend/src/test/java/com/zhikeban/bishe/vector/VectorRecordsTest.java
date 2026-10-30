package com.zhikeban.bishe.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量检索值对象单测（`ENG-013`）—— `SearchQuery.of` 的课程预过滤口径与其余 record 的字段契约。
 */
class VectorRecordsTest {

    @Test
    void ofBuildsACourseScopedQueryWithoutEmbedding() {
        SearchQuery single = SearchQuery.of("余弦相似度", 2L, 8);

        assertThat(single.text()).isEqualTo("余弦相似度");
        assertThat(single.embedding()).isNull();
        assertThat(single.courseIds()).containsExactly(2L);
        assertThat(single.topK()).isEqualTo(8);
    }

    @Test
    void ofToleratesANullCourseIdMeaningNoPreFilter() {
        assertThat(SearchQuery.of("q", null, 5).courseIds()).isEmpty();
    }

    @Test
    void recordsExposeTheirComponents() {
        VectorRecord record = new VectorRecord(1L, 2L, 2, new float[]{1f, 0f}, "{\"chapter\":\"第1章\"}");
        assertThat(record.chunkId()).isEqualTo(1L);
        assertThat(record.courseId()).isEqualTo(2L);
        assertThat(record.dim()).isEqualTo(2);
        assertThat(record.embedding()).containsExactly(1f, 0f);
        assertThat(record.metadata()).contains("第1章");

        SearchHit hit = new SearchHit(1L, 2L, 3L, 0.5d, "content", "metadata");
        assertThat(hit.score()).isEqualTo(0.5d);
        assertThat(hit.materialId()).isEqualTo(3L);

        RebuildReport report = new RebuildReport(2L, 10, 3, 12L, "mysql");
        assertThat(report.processed()).isEqualTo(10);
        assertThat(report.deleted()).isEqualTo(3);
        assertThat(report.provider()).isEqualTo("mysql");

        assertThat(List.of(hit)).hasSize(1);
    }
}
