package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.mapper.MaterialMapper;
import com.zhikeban.bishe.service.CourseService;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PermissionFilter` 单测（`ENG-013`）—— RAG 链路的**唯一安全咽喉**：
 * 课程级 ＋ 资料级 ＋ 成员级三级过滤，且**宁空不漏**。
 */
class PermissionFilterTest {

    private CourseService courseService;
    private MaterialMapper materialMapper;
    private PermissionFilter filter;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        materialMapper = mock(MaterialMapper.class);
        filter = new PermissionFilter(courseService, materialMapper);
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
    }

    private static RetrievedChunk rc(Long chunkId, Long materialId, String content) {
        return new RetrievedChunk(chunkId, materialId, "资料", "第1章", 1, 1.0d, content);
    }

    private static Material material(long id, long courseId) {
        Material m = new Material();
        m.setId(id);
        m.setCourseId(courseId);
        return m;
    }

    @Test
    void emptyOrNullCandidatesReturnEmptyWithoutTouchingAuth() {
        assertThat(filter.apply(List.of(), 1L, 2L)).isEmpty();
        assertThat(filter.apply(null, 1L, 2L)).isEmpty();
        verify(courseService, never()).isMember(any(), any());
    }

    @Test
    void nonMemberIsRejectedWith403() {
        when(courseService.isMember(2L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> filter.apply(List.of(rc(1L, 10L, "越权片段")), 1L, 2L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(403);
                    assertThat(e.getMessage()).contains("40301");
                });
    }

    @Test
    void memberKeepsOnlyChunksWhoseMaterialBelongsToThisCourse() {
        when(courseService.isMember(2L, 1L)).thenReturn(true);
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                material(10L, 2L), material(11L, 99L)));

        List<RetrievedChunk> out = filter.apply(List.of(
                rc(1L, 10L, "本课程"),
                rc(2L, 11L, "别的课程（向量侧滞后）"),
                rc(3L, 404L, "库里查不到的资料")), 1L, 2L);

        assertThat(out).extracting(RetrievedChunk::chunkId).containsExactly(1L);
    }

    @Test
    void chunksWithoutUsableMaterialIdAreDropped() {
        when(courseService.isMember(2L, 1L)).thenReturn(true);

        List<RetrievedChunk> out = filter.apply(List.of(
                rc(1L, null, "无归属"),
                rc(2L, 0L, "非法归属")), 1L, 2L);

        assertThat(out).isEmpty();
        // 没有可查的 materialId ⇒ 不必打库
        verify(materialMapper, never()).selectBatchIds(anyCollection());
    }

    @Test
    void anyRuntimeFailureMeansDropEverything() {
        when(courseService.isMember(2L, 1L)).thenReturn(true);
        when(materialMapper.selectBatchIds(anyCollection())).thenThrow(new IllegalStateException("库挂了"));

        assertThat(filter.apply(List.of(rc(1L, 10L, "候选")), 1L, 2L)).isEmpty();
    }

    @Test
    void authConclusionItselfIsNeverSwallowed() {
        // 鉴权异常必须上抛（否则越权请求会拿到 200 + 空结果）
        when(courseService.isMember(anyLong(), anyLong()))
                .thenThrow(ApiException.forbidden("非课程成员，无权检索该课程资料（40301）"));

        assertThatThrownBy(() -> filter.apply(List.of(rc(1L, 10L, "候选")), 1L, 2L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(403));
    }
}
