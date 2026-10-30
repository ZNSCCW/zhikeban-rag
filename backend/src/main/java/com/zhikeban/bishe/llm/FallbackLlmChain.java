package com.zhikeban.bishe.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成侧**降级链**（`ENG-012` 任务③；详设 §4.3.2 ⑤／§7.1）：
 * **① 本地优先（Ollama，`qwen2.5:7b`／`3b`）→ ② API 备选（OpenAI 兼容）→ ③ 全文检索兜底**。
 *
 * <p>★ **顺序是语义**（不是"两个都在文件里就算"）：本地模型先试，本地不可用才切 API。
 * 理由：本地模型零网络成本、数据不出机；API 只在本地不可用时作为备选。
 *
 * <p>★ **第三级不是"失败"**：本地与 API 均不可用时抛 {@link DegradeException}，
 * 由调用方转 {@link #fullTextFallback(PromptPayload)} —— 用 `material_chunk` 的关键词命中
 * 组织"资料中未找到依据"类回答，并把 {@code degraded} 置真。⇒ **链路不因供方不可用而中断**。
 *
 * <p>★ 三级分支数 = **3**（本地 / API / 全文兜底），与详设 §7.1 的分支一一对应。
 *
 * <p>★ 标 {@link Primary}：容器里同时有 Ollama / OpenAI / 本链三个实现 ⇒ 业务侧按类型注入时
 * **必须**拿到本链（本地优先 + 备选 + 兜底的语义只在链上成立）。
 */
@Service
@Primary
public class FallbackLlmChain implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackLlmChain.class);

    private final OllamaLlmClient ollama;
    private final OpenAiLlmClient openAi;

    private volatile String lastDegradeReason;

    public FallbackLlmChain(OllamaLlmClient ollama, OpenAiLlmClient openAi) {
        this.ollama = ollama;
        this.openAi = openAi;
    }

    /**
     * 主入口：按"本地优先 → API 备选 → 全文检索兜底"的顺序取流。
     * ★ 本地/API 在**订阅期**出错也一律转成 {@link DegradeException}，由调用方接第三级 —— 不静默吞掉。
     */
    @Override
    public Flux<String> stream(PromptPayload payload) {
        if (ollama != null && ollama.healthy()) {
            return ollama.stream(payload).onErrorResume(err -> {
                lastDegradeReason = "本地模型生成中断：" + err.getMessage();
                return Flux.error(new DegradeException("GENERATE", lastDegradeReason, err));
            });
        }
        String localReason = "本地模型不可用（ollama.healthy() = false）";
        if (openAi != null && openAi.healthy()) {
            return openAi.stream(payload).onErrorResume(err -> {
                lastDegradeReason = "API 备选生成中断：" + err.getMessage();
                return Flux.error(new DegradeException("GENERATE", lastDegradeReason, err));
            });
        }
        lastDegradeReason = localReason + "；API 备选不可用";
        throw new DegradeException("GENERATE", lastDegradeReason + " ⇒ 降级为全文检索兜底");
    }

    /**
     * **第三级：全文检索兜底**。把本次检索到的片段（`material_chunk` 的 BM25 / ngram 命中或
     * 向量命中）组织成可读回答，**逐条给出编号引用**；无命中时如实说"资料中未找到依据"。
     */
    public Flux<String> fullTextFallback(PromptPayload payload) {
        lastDegradeReason = "本地模型与 API 备选均不可用 ⇒ 第三级：全文检索兜底";
        log.warn("{}", lastDegradeReason);
        return Flux.fromIterable(chunk(buildFullTextAnswer(payload)));
    }

    /** 组装全文兜底正文（片段编号与 `CitationResolver` 的编号口径一致）。 */
    String buildFullTextAnswer(PromptPayload payload) {
        List<String> evidences = payload == null || payload.evidences() == null
                ? List.of() : payload.evidences();
        if (evidences.isEmpty()) {
            return "资料中未找到依据。";
        }
        StringBuilder sb = new StringBuilder("（本地模型与 API 均不可用，以下为资料全文检索结果）\n");
        for (int i = 0; i < evidences.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(evidences.get(i)).append('\n');
        }
        return sb.toString();
    }

    /** 按句切分成分片（流式下发的粒度）。 */
    static List<String> chunk(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        for (String piece : text.split("(?<=\\n)|(?<=。)|(?<=；)")) {
            if (!piece.isEmpty()) {
                out.add(piece);
            }
        }
        return out.isEmpty() ? List.of(text) : out;
    }

    @Override
    public String complete(PromptPayload payload) {
        StringBuilder sb = new StringBuilder();
        try {
            for (String piece : stream(payload).toIterable()) {
                sb.append(piece);
            }
        } catch (DegradeException e) {
            for (String piece : fullTextFallback(payload).toIterable()) {
                sb.append(piece);
            }
        }
        return sb.toString();
    }

    @Override
    public String provider() {
        return "fallback-chain";
    }

    @Override
    public boolean healthy() {
        return (ollama != null && ollama.healthy()) || (openAi != null && openAi.healthy());
    }

    /** 最近一次降级原因（自检/埋点用，**不作任何判据**）。 */
    public String lastDegradeReason() {
        return lastDegradeReason;
    }
}
