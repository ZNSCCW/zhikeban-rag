package com.zhikeban.bishe.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `FallbackLlmChain` 单测（`ENG-013`）—— 生成侧三级降级链：
 * **① 本地优先（Ollama）→ ② API 备选 → ③ 全文检索兜底**。
 * ★ 顺序是语义：本地健康就**绝不**走 API；两级都不可用才抛 `DegradeException` 交给第三级。
 */
class FallbackLlmChainTest {

    private OllamaLlmClient ollama;
    private OpenAiLlmClient openAi;
    private FallbackLlmChain chain;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaLlmClient.class);
        openAi = mock(OpenAiLlmClient.class);
        chain = new FallbackLlmChain(ollama, openAi);
    }

    private static PromptPayload payload(String... evidences) {
        return new PromptPayload("系统提示", "问题？", List.of(evidences), List.of("上轮"), 0.2d, 512);
    }

    @Test
    void prefersLocalModelWhenItIsHealthy() {
        when(ollama.healthy()).thenReturn(true);
        when(ollama.stream(any())).thenReturn(Flux.just("本地", "答案"));

        assertThat(chain.stream(payload("证据")).collectList().block()).containsExactly("本地", "答案");
        verify(openAi, never()).stream(any());
    }

    @Test
    void fallsToApiBackupOnlyWhenLocalIsUnavailable() {
        when(ollama.healthy()).thenReturn(false);
        when(openAi.healthy()).thenReturn(true);
        when(openAi.stream(any())).thenReturn(Flux.just("API", "答案"));

        assertThat(chain.stream(payload("证据")).collectList().block()).containsExactly("API", "答案");
    }

    @Test
    void throwsDegradeExceptionWhenBothProvidersAreDown() {
        when(ollama.healthy()).thenReturn(false);
        when(openAi.healthy()).thenReturn(false);

        assertThatThrownBy(() -> chain.stream(payload("证据")))
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("降级为全文检索兜底");
        assertThat(chain.lastDegradeReason()).contains("本地模型不可用").contains("API 备选不可用");
    }

    @Test
    void localStreamFailureIsTranslatedIntoDegradeException() {
        when(ollama.healthy()).thenReturn(true);
        when(ollama.stream(any())).thenReturn(Flux.error(new RuntimeException("连接被重置")));

        assertThatThrownBy(() -> chain.stream(payload("证据")).collectList().block())
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("本地模型生成中断");
    }

    @Test
    void apiStreamFailureIsTranslatedIntoDegradeException() {
        when(ollama.healthy()).thenReturn(false);
        when(openAi.healthy()).thenReturn(true);
        when(openAi.stream(any())).thenReturn(Flux.error(new RuntimeException("502")));

        assertThatThrownBy(() -> chain.stream(payload("证据")).collectList().block())
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("API 备选生成中断");
    }

    @Test
    void fullTextFallbackNumbersEveryEvidenceAndSplitsIntoStreamPieces() {
        List<String> pieces = chain.fullTextFallback(payload("甲片段。", "乙片段。")).collectList().block();

        assertThat(pieces).isNotNull();
        assertThat(String.join("", pieces))
                .contains("本地模型与 API 均不可用")
                .contains("[1] 甲片段。")
                .contains("[2] 乙片段。");
        assertThat(chain.lastDegradeReason()).contains("第三级");
    }

    @Test
    void fullTextFallbackSaysSoWhenNothingWasRetrieved() {
        assertThat(chain.buildFullTextAnswer(payload())).isEqualTo("资料中未找到依据。");
        assertThat(chain.buildFullTextAnswer(null)).isEqualTo("资料中未找到依据。");
        assertThat(String.join("", chain.fullTextFallback(payload()).collectList().block()))
                .isEqualTo("资料中未找到依据。");
    }

    @Test
    void completeReturnsConcatenationFromTheHealthyProvider() {
        when(ollama.healthy()).thenReturn(true);
        when(ollama.stream(any())).thenReturn(Flux.just("甲", "乙"));

        assertThat(chain.complete(payload("证据"))).isEqualTo("甲乙");
    }

    @Test
    void completeFallsBackToFullTextWhenEverythingIsDown() {
        when(ollama.healthy()).thenReturn(false);
        when(openAi.healthy()).thenReturn(false);

        String answer = chain.complete(payload("甲片段。"));

        assertThat(answer).contains("[1] 甲片段。");
    }

    @Test
    void providerAndHealthAreTheComposedOnes() {
        assertThat(chain.provider()).isEqualTo("fallback-chain");
        assertThat(chain.healthy()).isFalse();

        when(openAi.healthy()).thenReturn(true);
        assertThat(chain.healthy()).isTrue();
    }

    @Test
    void chunkSplitsOnSentenceAndLineBoundaries() {
        assertThat(FallbackLlmChain.chunk(null)).isEmpty();
        assertThat(FallbackLlmChain.chunk("")).isEmpty();
        assertThat(FallbackLlmChain.chunk("第一句。第二句；第三句")).containsExactly("第一句。", "第二句；", "第三句");
        assertThat(FallbackLlmChain.chunk("无标点的一句话")).containsExactly("无标点的一句话");
        assertThat(FallbackLlmChain.chunk("甲\n乙")).containsExactly("甲\n", "乙");
    }
}
