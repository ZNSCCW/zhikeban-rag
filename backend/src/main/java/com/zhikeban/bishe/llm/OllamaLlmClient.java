package com.zhikeban.bishe.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 大模型调用 · **本地实现（Ollama）**（`ENG-012` 任务③；详设 §4.3.2 ⑤ 的本地优先分支）。
 *
 * <p>★ 模型：{@code qwen2.5:7b}（主）与 {@code qwen2.5:3b}（拉不动主模型时退到它）——
 * 两个**都在**配置里（`application.yml` 的 {@code zhikeban.llm.ollama.models}），不写死在代码里。
 *
 * <p>★ {@link #healthy()} 是**真探活**：GET 本机 Ollama 的 {@code /api/tags}，不是
 * "返回 {@code true}"。接口不是 HTTP 可用即算健康 —— 只有 HTTP 200 才算。
 *
 * <p>★ {@link #stream(PromptPayload)} 走 {@code /api/generate} 的 **NDJSON 流式**返回：
 * 逐行解析 {@code {"response": "…"}} 并作为分片下发；调用方拿到的是**真流**，不是"一次性结果切几段"。
 */
@Service
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final String baseUrl;
    private final List<String> models;
    private final int timeoutMs;

    public OllamaLlmClient(
            @Value("${zhikeban.llm.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${zhikeban.llm.ollama.models:qwen2.5:7b,qwen2.5:3b}") String models,
            @Value("${zhikeban.llm.ollama.timeout-ms:120000}") int timeoutMs) {
        this.baseUrl = baseUrl;
        this.models = Stream.of(models.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Flux<String> stream(PromptPayload payload) {
        // 主模型拉不动时退到小模型（同一供方内的次级尝试，不算换供方）
        List<String> tryOrder = models.isEmpty() ? List.of("qwen2.5:7b") : models;
        RuntimeException last = null;
        for (String model : tryOrder) {
            try {
                return streamWith(model, payload);
            } catch (RuntimeException e) {
                last = e;
                log.warn("本地模型 {} 调用失败，尝试下一个：{}", model, e.getMessage());
            }
        }
        throw new DegradeException("GENERATE", "本地模型全部失败：" + (last == null ? "无可用模型" : last.getMessage()), last);
    }

    /** 真流式：{@code /api/generate} 的 NDJSON 逐行下发（`stream: true`）。 */
    private Flux<String> streamWith(String model, PromptPayload payload) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", render(payload),
                    "stream", true,
                    "options", Map.of("temperature", payload == null ? 0.2 : payload.temperature(),
                            "num_predict", payload == null ? 256 : Math.max(payload.maxTokens(), 16))));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new DegradeException("GENERATE", "Ollama /api/generate 返回 " + resp.statusCode());
            }
            return Flux.fromStream(() -> ndjson(resp.body()).stream())
                    .filter(s -> !s.isEmpty());
        } catch (DegradeException e) {
            throw e;
        } catch (Exception e) {
            throw new DegradeException("GENERATE", "调用本地模型失败：" + e.getMessage(), e);
        }
    }

    /** 把 NDJSON 的每一行解析成正文分片（读完即关流）。 */
    private List<String> ndjson(InputStream in) {
        List<String> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = mapper.readTree(line);
                JsonNode piece = node.path("response");
                if (piece.isTextual() && !piece.asText().isEmpty()) {
                    out.add(piece.asText());
                }
            }
        } catch (Exception e) {
            throw new DegradeException("GENERATE", "解析本地模型流失败：" + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public String complete(PromptPayload payload) {
        StringBuilder sb = new StringBuilder();
        for (String piece : stream(payload).toIterable()) {
            sb.append(piece);
        }
        return sb.toString();
    }

    @Override
    public String provider() {
        return "ollama";
    }

    /** 真探活：GET {@code /api/tags}，HTTP 200 才算健康（不是 {@code return true}）。 */
    @Override
    public boolean healthy() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Ollama /api/tags 探活失败：{}", e.getMessage());
            return false;
        }
    }

    /** 把载荷渲染成提示词文本（系统提示 ＋ 编号片段 ＋ 最近对话 ＋ 当前问题）。 */
    static String render(PromptPayload payload) {
        if (payload == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (payload.systemPrompt() != null && !payload.systemPrompt().isBlank()) {
            sb.append(payload.systemPrompt()).append("\n\n");
        }
        if (payload.evidences() != null && !payload.evidences().isEmpty()) {
            for (int i = 0; i < payload.evidences().size(); i++) {
                sb.append('[').append(i + 1).append("] ").append(payload.evidences().get(i)).append('\n');
            }
            sb.append('\n');
        }
        if (payload.history() != null) {
            for (String turn : payload.history()) {
                sb.append(turn).append('\n');
            }
        }
        sb.append("问题：").append(payload.question() == null ? "" : payload.question());
        return sb.toString();
    }
}
