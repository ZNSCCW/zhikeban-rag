package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.llm.DegradeException;
import com.zhikeban.bishe.llm.FallbackLlmChain;
import com.zhikeban.bishe.llm.PromptPayload;
import com.zhikeban.bishe.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `RagOrchestrator` 单测（`ENG-013`）—— 六阶段编排（`ENG-012` 任务①）：
 * ① 提问受理 → ② 混合检索 → ③ 权限过滤 → ④ 提示词组装 → ⑤ 流式生成 → ⑥ 引用回填，
 * 并**逐阶段留痕**（`traces()`）。
 */
class RagOrchestratorTest {

    private HybridRetriever retriever;
    private PermissionFilter permissionFilter;
    private PromptAssembler promptAssembler;
    private FallbackLlmChain llmChain;
    private CitationResolver citationResolver;
    private RagOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        retriever = mock(HybridRetriever.class);
        permissionFilter = mock(PermissionFilter.class);
        promptAssembler = mock(PromptAssembler.class);
        llmChain = mock(FallbackLlmChain.class);
        citationResolver = mock(CitationResolver.class);
        orchestrator = new RagOrchestrator(retriever, permissionFilter, promptAssembler, llmChain, citationResolver);

        when(retriever.retrieve(anyString(), any(), anyInt())).thenReturn(List.of(
                new RetrievedChunk(1L, 10L, "资料A", "第1章", 1, 1.0d, "甲"),
                new RetrievedChunk(2L, 11L, "资料B", "第2章", 2, 0.5d, "乙")));
        when(permissionFilter.apply(anyList(), any(), any())).thenReturn(List.of(
                new RetrievedChunk(1L, 10L, "资料A", "第1章", 1, 1.0d, "甲")));
        when(promptAssembler.assemble(anyList(), anyList(), anyString())).thenReturn(
                new PromptPayload("系统提示", "问题？", List.of("资料A（第1章 · 第 1 页）：甲"), List.of(), 0.2d, 512));
    }

    private RagAnswer answer(boolean degraded, String reason, int citations) {
        List<Citation> cs = citations == 0 ? List.of()
                : List.of(new Citation(10L, "资料A", "第1章", 1, "甲"));
        return new RagAnswer(1L, "答案正文", cs, degraded, reason, false);
    }

    /**
     * 让 {@code CitationResolver} 把**生成阶段真实的** `degraded`/`reason` 透传进 `RagAnswer`。
     *
     * <p>★ 这样 `result.degraded()` 断的就是 `generate()` 的输出，而不是桩里写死的值
     * （`§ENG-014` `D2` 要求"正常路径 ⇒ false/null"必须验到真行为）。
     */
    private void stubResolve() {
        when(citationResolver.resolve(anyList(), anyList(), nullable(Long.class), anyBoolean(), nullable(String.class)))
                .thenAnswer(invocation -> answer(invocation.getArgument(3), invocation.getArgument(4), 1));
    }

    @Test
    void happyPathRunsAllSixStagesAndLeavesTrace() {
        when(llmChain.stream(any())).thenReturn(Flux.just("答案", "正文"));
        stubResolve();

        RagAnswer result = orchestrator.run(new RagRequest(2L, null, 7L, "余弦相似度？", true));

        assertThat(result.content()).isEqualTo("答案正文");
        assertThat(result.citations()).hasSize(1);
        // D2：正常路径 ⇒ degraded=false 且 degradeReason=null（值由 generate() 透传，非桩里写死）
        assertThat(result.degraded()).isFalse();
        assertThat(result.degradeReason()).isNull();

        List<RagTrace> traces = orchestrator.traces();
        assertThat(traces).extracting(RagTrace::stage)
                .containsExactly("VALIDATE", "RETRIEVE", "FILTER", "PROMPT", "GENERATE", "CITE");
        assertThat(traces).allSatisfy(t -> assertThat(t.ok()).isTrue());
        assertThat(traces).extracting(RagTrace::label)
                .containsExactly("① 提问受理", "② 混合检索", "③ 权限过滤", "④ 提示词组装", "⑤ 流式生成", "⑥ 引用回填");
        assertThat(traces.get(4).detail()).isEqualTo("本地优先（Ollama）真跑，收到 2 个流式分片");
        assertThat(traces.get(5).detail()).contains("引用卡片 1 条");
        assertThat(traces).allSatisfy(t -> assertThat(t.ms()).isGreaterThanOrEqualTo(0L));
    }

    @Test
    void blankQuestionIsRejectedBeforeAnyRetrieval() {
        assertThatThrownBy(() -> orchestrator.run(new RagRequest(2L, null, 7L, "   ", true)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));

        assertThat(orchestrator.traces()).hasSize(1);
        assertThat(orchestrator.traces().get(0).ok()).isFalse();
        assertThat(orchestrator.traces().get(0).stage()).isEqualTo("VALIDATE");
        verify(retriever, never()).retrieve(anyString(), any(), anyInt());
    }

    @Test
    void nullRequestIsAlsoRejectedAsBadRequest() {
        assertThatThrownBy(() -> orchestrator.run(null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void synchronousDegradeExceptionSwitchesToFullTextFallbackAndReportsDegraded() {
        // FallbackLlmChain 在两个供方都不可用时**同步抛出** DegradeException ⇒ 第三级兜底 + degraded=true
        when(llmChain.stream(any())).thenThrow(new DegradeException("GENERATE", "本地模型与 API 备选均不可用"));
        when(llmChain.fullTextFallback(any())).thenReturn(Flux.just("兜底正文 [1]"));
        when(citationResolver.resolve(anyList(), anyList(), nullable(Long.class), anyBoolean(), nullable(String.class)))
                .thenReturn(answer(true, "本地模型与 API 备选均不可用", 1));

        RagAnswer result = orchestrator.run(new RagRequest(2L, null, 7L, "问题？", true));

        assertThat(result.degraded()).isTrue();
        assertThat(result.degradeReason()).isEqualTo("本地模型与 API 备选均不可用");
        assertThat(orchestrator.traces().get(4).detail())
                .isEqualTo("降级为全文检索兜底：本地模型与 API 备选均不可用");
        verify(llmChain).fullTextFallback(any());
    }

    @Test
    void degradeExceptionEmittedInsideTheStreamAlsoFallsBack() {
        when(llmChain.stream(any())).thenReturn(Flux.error(new DegradeException("GENERATE", "本地模型生成中断")));
        when(llmChain.fullTextFallback(any())).thenReturn(Flux.just("兜底正文 [1]"));
        stubResolve();

        RagAnswer result = orchestrator.run(new RagRequest(2L, null, 7L, "问题？", true));

        ArgumentCaptor<Boolean> degraded = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(citationResolver).resolve(anyList(), anyList(), nullable(Long.class), degraded.capture(),
                reason.capture());
        verify(llmChain).fullTextFallback(any());

        // ★★ ISSUE-32（`§ENG-014` `D1`／`D4`）：`onErrorResume` 分支**确实走了全文兜底**，
        //    因此 `degraded` 必须为 true、原因必须如实带出（早先这里锁的是 `false`，已由 GM 裁定改实现）。
        assertThat(degraded.getValue()).isTrue();
        assertThat(reason.getValue()).isEqualTo("本地模型生成中断");
        assertThat(orchestrator.traces().get(4).ok()).isTrue();
        assertThat(orchestrator.traces().get(4).detail()).isEqualTo("降级为全文检索兜底：本地模型生成中断");
    }

    @Test
    void tracesAreSnapshotCopiesNotLiveViews() {
        when(llmChain.stream(any())).thenReturn(Flux.just("x"));
        stubResolve();

        orchestrator.run(new RagRequest(2L, null, 7L, "问题？", true));
        List<RagTrace> first = orchestrator.traces();
        orchestrator.run(new RagRequest(2L, null, 7L, "问题？", true));

        assertThat(first).hasSize(6);
        assertThat(orchestrator.traces()).hasSize(6);
        assertThat(first).isNotSameAs(orchestrator.traces());
    }
}
