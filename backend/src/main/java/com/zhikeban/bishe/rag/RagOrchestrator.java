package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.llm.DegradeException;
import com.zhikeban.bishe.llm.FallbackLlmChain;
import com.zhikeban.bishe.llm.PromptPayload;
import com.zhikeban.bishe.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * **RAG 主链路编排**（`ENG-012` 任务①；详设 §4.3.2）—— 按六阶段串起各组件：
 *
 * <pre>
 *   ① 提问受理 → ② 混合检索 → ③ 权限过滤 → ④ 提示词组装 → ⑤ 流式生成 → ⑥ 引用回填
 * </pre>
 *
 * <p>★ 编排层**只做编排**：每个阶段的判定逻辑都在各自组件里（检索在 `HybridRetriever`、
 * 越权防线在 `PermissionFilter`、预算与截断在 `PromptAssembler`、降级链在 `FallbackLlmChain`、
 * 越界引用剔除在 `CitationResolver`）。⇒ 换实现不动编排。
 *
 * <p>★ 每个阶段都留 {@link RagTrace}（阶段名 / 是否按预期完成 / 人读细节 / 耗时），
 * 既供埋点（`rag.stage.duration`），也供冒烟脚本"**逐阶段可核**"。
 *
 * <p>★ 降级**不算失败**：`⑤` 走全文检索兜底时阶段仍记"完成"，只把 `degraded` 与原因带进回答
 * （`RagAnswer.degraded`）—— 链路不因供方不可用而中断。
 */
@Service
public class RagOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RagOrchestrator.class);

    /** 生成阶段上限（详设：超过 30s 触发降级；此处给足本地模型冷启动时间，由降级链兜底）。 */
    private static final int GENERATE_TIMEOUT_SECONDS = 180;

    /** 融合后保留片数（详设 §4.3.3：Top5）。 */
    private static final int FUSION_TOP_K = 5;

    private final HybridRetriever hybridRetriever;
    private final PermissionFilter permissionFilter;
    private final PromptAssembler promptAssembler;
    private final FallbackLlmChain llmChain;
    private final CitationResolver citationResolver;

    private final List<RagTrace> traces = new ArrayList<>();

    public RagOrchestrator(HybridRetriever hybridRetriever, PermissionFilter permissionFilter,
                           PromptAssembler promptAssembler, FallbackLlmChain llmChain,
                           CitationResolver citationResolver) {
        this.hybridRetriever = hybridRetriever;
        this.permissionFilter = permissionFilter;
        this.promptAssembler = promptAssembler;
        this.llmChain = llmChain;
        this.citationResolver = citationResolver;
    }

    /**
     * 跑一次完整的 RAG 链路。
     *
     * @param request 请求（课程 / 用户 / 问题）
     * @return 带引用卡片的回答
     */
    public synchronized RagAnswer run(RagRequest request) {
        traces.clear();

        long t0 = System.currentTimeMillis();
        if (request == null || request.question() == null || request.question().isBlank()) {
            record("① 提问受理", RagStage.VALIDATE, false, "问题为空 ⇒ 拒绝（不进入检索）", t0);
            throw ApiException.badRequest("提问不能为空");
        }
        record("① 提问受理", RagStage.VALIDATE, true,
                "courseId=" + request.courseId() + " ｜ userId=" + request.userId(), t0);

        t0 = System.currentTimeMillis();
        List<RetrievedChunk> candidates = hybridRetriever.retrieve(request.question(), request.courseId(), FUSION_TOP_K);
        record("② 混合检索", RagStage.RETRIEVE, true,
                "召回 " + candidates.size() + " 片（向量一路 ＋ 全文一路 → RRF 融合）", t0);

        t0 = System.currentTimeMillis();
        List<RetrievedChunk> allowed = permissionFilter.apply(candidates, request.userId(), request.courseId());
        record("③ 权限过滤", RagStage.FILTER, true,
                "保留 " + allowed.size() + " / " + candidates.size() + " 片（越权项一律丢弃，宁空不漏）", t0);

        t0 = System.currentTimeMillis();
        PromptPayload payload = promptAssembler.assemble(allowed, List.of(), request.question());
        record("④ 提示词组装", RagStage.PROMPT, true,
                "编号片段 " + payload.evidences().size() + " 片 ＋ 系统提示 ＋ 最近 " + payload.history().size() + " 轮", t0);

        t0 = System.currentTimeMillis();
        Generation generation = generate(payload);
        record("⑤ 流式生成", RagStage.GENERATE, true, generation.detail(), t0);

        t0 = System.currentTimeMillis();
        RagAnswer answer = citationResolver.resolve(generation.pieces(), allowed, request.chatId(),
                generation.degraded(), generation.reason());
        record("⑥ 引用回填", RagStage.CITE, !answer.citations().isEmpty(),
                "引用卡片 " + answer.citations().size() + " 条（越界编号已剔除）", t0);

        log.info("RAG 链路完成：course={} 证据 {} 片 引用 {} 条 degraded={}",
                request.courseId(), allowed.size(), answer.citations().size(), answer.degraded());
        return answer;
    }

    /**
     * ⑤ 流式生成：本地优先 → API 备选 → **全文检索兜底**。
     * ★ 供方不可用**不抛给上层**：这里接住 `DegradeException`，改走第三级并把降级如实带进回答。
     *
     * <p>★★ `ISSUE-32`（`§ENG-014` `D` 组）：**两条降级分支都必须置 `degraded = true`**。
     * 早先 `onErrorResume` 分支只"用了全文兜底"却返回 `degraded=false`，与类注释
     * "把降级如实带进回答"不一致 —— 那会让前端/日志误以为答案来自模型。
     * 现在用 {@link java.util.concurrent.atomic.AtomicReference} 在恢复回调里记下
     * `DegradeException`，流结束后据此判定是否降级并带上原因（**不得静默**，`D4`）。
     */
    private Generation generate(PromptPayload payload) {
        AtomicReference<DegradeException> resumed = new AtomicReference<>();
        try {
            Flux<String> flux = llmChain.stream(payload)
                    .onErrorResume(DegradeException.class, err -> {
                        resumed.set(err);
                        log.warn("生成阶段降级（{}），改用全文检索兜底", err.getMessage());
                        return llmChain.fullTextFallback(payload);
                    });
            List<String> pieces = flux.collectList().block(Duration.ofSeconds(GENERATE_TIMEOUT_SECONDS));
            DegradeException degradedBy = resumed.get();
            return new Generation(pieces == null ? List.of() : pieces,
                    degradedBy != null,
                    degradedBy == null ? null : degradedBy.getMessage());
        } catch (DegradeException e) {
            log.warn("生成阶段降级（{}），改用全文检索兜底", e.getMessage());
            List<String> pieces = llmChain.fullTextFallback(payload).collectList().block(Duration.ofSeconds(30));
            return new Generation(pieces == null ? List.of() : pieces, true, e.getMessage());
        }
    }

    private void record(String label, RagStage stage, boolean ok, String detail, long startMs) {
        traces.add(new RagTrace(label, stage.name(), ok, detail, System.currentTimeMillis() - startMs));
    }

    /** 最近一次链路执行的逐阶段留痕（供冒烟脚本"逐阶段可核"）。 */
    public synchronized List<RagTrace> traces() {
        return new ArrayList<>(traces);
    }

    /** 生成阶段的结果（分片 / 是否降级 / 降级原因）。 */
    private record Generation(List<String> pieces, boolean degraded, String reason) {

        String detail() {
            if (degraded) {
                return "降级为全文检索兜底：" + reason;
            }
            return "本地优先（Ollama）真跑，收到 " + pieces.size() + " 个流式分片";
        }
    }
}
